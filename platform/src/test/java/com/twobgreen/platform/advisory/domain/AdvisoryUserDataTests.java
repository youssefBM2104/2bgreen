package com.twobgreen.platform.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import com.twobgreen.platform.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdvisoryUserDataTests {

	/** PostgreSQL keeps microseconds. */
	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

	/** A fresh user per test: tests share one database and must not see each other's rows. */
	private final String user = UUID.randomUUID().toString();

	@Autowired
	private UsageRepository usages;

	@Autowired
	private UsageLogEntryRepository log;

	@Autowired
	private RegionPreferenceRepository regions;

	@Test
	void usageIsStoredAndReadBackWithItsGeneratedId() {
		Usage saved = usages.save(new Usage(null, user, "dishwasher", 180, LocalTime.of(7, 30), NOW));

		assertThat(saved.id()).isNotNull();
		assertThat(usages.findByIdAndUserId(saved.id(), user)).contains(saved);
	}

	@Test
	void usageWithoutADeadlineIsStored() {
		Usage saved = usages.save(new Usage(null, user, "EV charge", 360, null, NOW));

		assertThat(usages.findById(saved.id()).orElseThrow().finishBefore()).isNull();
	}

	@Test
	void usageOfAnotherUserIsNotFound() {
		Usage saved = usages.save(new Usage(null, user, "dishwasher", 180, null, NOW));

		assertThat(usages.findByIdAndUserId(saved.id(), "someone-else")).isEmpty();
		assertThat(usages.findByUserIdOrderByLabel("someone-else")).isEmpty();
	}

	@Test
	void sameLabelTwiceForOneUserIsRefused() {
		usages.save(new Usage(null, user, "dishwasher", 180, null, NOW));

		assertThatThrownBy(() -> usages.save(new Usage(null, user, "dishwasher", 60, null, NOW)))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void durationOutOfRangeIsRefusedByTheDatabase() {
		assertThatThrownBy(() -> usages.save(new Usage(null, user, "nothing", 0, null, NOW)))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> usages.save(new Usage(null, user, "two days", 2880, null, NOW)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void logEntriesAreReadByPeriodOldestFirst() {
		Instant monday = Instant.parse("2026-11-02T00:00:00Z");
		log.save(new UsageLogEntry(null, user, "dishwasher", monday.plus(2, ChronoUnit.DAYS), 180, NOW));
		log.save(new UsageLogEntry(null, user, "EV charge", monday, 360, NOW));
		log.save(new UsageLogEntry(null, user, "next week", monday.plus(7, ChronoUnit.DAYS), 60, NOW));

		assertThat(log.findOfUserBetween(user, monday, monday.plus(7, ChronoUnit.DAYS)))
				.extracting(UsageLogEntry::usageLabel).containsExactly("EV charge", "dishwasher");
	}

	@Test
	void logEntryKeepsItsInstantInUtc() {
		Instant start = Instant.parse("2026-03-29T01:30:00Z");

		UsageLogEntry saved = log.save(new UsageLogEntry(null, user, "dishwasher", start, 180, NOW));

		assertThat(log.findByIdAndUserId(saved.id(), user).orElseThrow().startedAt()).isEqualTo(start);
	}

	@Test
	void logEntrySurvivesTheDeletionOfItsUsage() {
		Usage usage = usages.save(new Usage(null, user, "dishwasher", 180, null, NOW));
		log.save(new UsageLogEntry(null, user, usage.label(), NOW, 180, NOW));

		usages.deleteById(usage.id());

		assertThat(log.findOfUserBetween(user, NOW.minusSeconds(1), NOW.plusSeconds(1))).hasSize(1);
	}

	@Test
	void oneRegionPerUser() {
		regions.save(new RegionPreference(null, user, "93", NOW));

		assertThat(regions.findByUserId(user)).map(RegionPreference::regionCode).contains("93");
		assertThatThrownBy(() -> regions.save(new RegionPreference(null, user, "32", NOW)))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void regionIsChangedInPlace() {
		RegionPreference saved = regions.save(new RegionPreference(null, user, "93", NOW));

		regions.save(new RegionPreference(saved.id(), user, "32", NOW));

		assertThat(regions.findByUserId(user)).map(RegionPreference::regionCode).contains("32");
	}

	@Test
	void anythingButATwoDigitCodeIsRefusedAsARegion() {
		assertThatThrownBy(() -> regions.save(new RegionPreference(null, user, "Nice, 06000", NOW)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingAUserRemovesAllTheirRowsAndOnlyTheirs() {
		String other = UUID.randomUUID().toString();
		usages.save(new Usage(null, user, "dishwasher", 180, null, NOW));
		log.save(new UsageLogEntry(null, user, "dishwasher", NOW, 180, NOW));
		regions.save(new RegionPreference(null, user, "93", NOW));
		usages.save(new Usage(null, other, "dishwasher", 180, null, NOW));

		assertThat(usages.deleteAllOfUser(user)).isEqualTo(1);
		assertThat(log.deleteAllOfUser(user)).isEqualTo(1);
		assertThat(regions.deleteAllOfUser(user)).isEqualTo(1);

		assertThat(usages.findByUserIdOrderByLabel(user)).isEmpty();
		assertThat(regions.findByUserId(user)).isEmpty();
		assertThat(usages.findByUserIdOrderByLabel(other)).hasSize(1);
	}

	@Test
	void oldLogEntriesArePurgedByDate() {
		Instant old = Instant.parse("2001-01-01T00:00:00Z");
		UsageLogEntry entry = log.save(new UsageLogEntry(null, user, "dishwasher", old, 180, NOW));

		assertThat(log.deleteStartedBefore(old.plusSeconds(1))).isGreaterThanOrEqualTo(1);

		assertThat(log.findById(entry.id())).isEmpty();
	}
}
