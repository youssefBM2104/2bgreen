package com.twobgreen.gridintelligence.infrastructure.odre;

import static com.twobgreen.gridintelligence.infrastructure.odre.FakeOdreServer.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

class NationalConsolidatedMappingTests {

	private static final OdreResponses RESPONSES = new OdreResponses();

	private static final String CONSOLIDATED = "Données consolidées";

	@Test
	void measuredRowGivesAFullConsolidatedSnapshot() {
		// Real export line: 2026-08-31 23:30 Paris, "gaz_cogen" and "ech_comm_*" sent as text but not used
		Optional<GridSnapshot> snapshot = NationalConsolidatedMapping.toSnapshot(
				lines("national-consdef-export-sample.jsonl").getFirst());

		assertThat(snapshot).contains(new GridSnapshot(
				"national",
				Instant.parse("2026-08-31T21:30:00Z"),
				new BigDecimal("29"),
				43979,
				43000,
				44200,
				new Production(37002, 3219, 15, 55, null, 3758, 1, 4311, 1179),
				-50,
				Quality.CONSOLIDATED,
				false));
	}

	@Test
	void everyLineOfTheExportSampleGivesASnapshotOnTheHalfHour() {
		List<GridSnapshot> snapshots = lines("national-consdef-export-sample.jsonl").stream()
				.map(NationalConsolidatedMapping::toSnapshot)
				.flatMap(Optional::stream)
				.toList();

		assertThat(snapshots).extracting(GridSnapshot::observedAt).containsExactly(
				Instant.parse("2026-08-31T21:30:00Z"),
				Instant.parse("2026-08-31T21:00:00Z"),
				Instant.parse("2026-08-31T20:30:00Z"));
	}

	@Test
	void definitiveNatureGivesDefinitiveQuality() {
		assertThat(NationalConsolidatedMapping.toSnapshot(row("Données définitives", "01:00", "49970"))
				.orElseThrow().quality()).isEqualTo(Quality.DEFINITIVE);
	}

	@Test
	void placeholderRowWithoutConsumptionGivesNoSnapshot() {
		// The :15 and :45 rows: the real step of the dataset is 30 min
		assertThat(NationalConsolidatedMapping.toSnapshot(row(CONSOLIDATED, "23:45", null))).isEmpty();
		assertThat(NationalConsolidatedMapping.toSnapshot(row(CONSOLIDATED, "23:45", "ND"))).isEmpty();
	}

	@Test
	void springDstPhantomsAreDroppedAndTheRealRowsKept() {
		// 2026-03-29: Paris goes from 02:00 to 03:00, yet the data holds 02:xx and 03:xx rows for the same instant
		List<GridSnapshot> snapshots = lines("national-consdef-dst-spring-2026-trimmed.jsonl").stream()
				.map(NationalConsolidatedMapping::toSnapshot)
				.flatMap(Optional::stream)
				.toList();

		// One row per instant: an upsert on (scope, observed_at) could not absorb a duplicate in one batch
		assertThat(snapshots).extracting(GridSnapshot::observedAt).doesNotHaveDuplicates();
		assertThat(snapshots).extracting(GridSnapshot::observedAt).containsExactlyInAnyOrder(
				Instant.parse("2026-03-28T23:00:00Z"),
				Instant.parse("2026-03-28T23:30:00Z"),
				Instant.parse("2026-03-29T00:00:00Z"),
				Instant.parse("2026-03-29T00:30:00Z"),
				Instant.parse("2026-03-29T01:00:00Z"),
				Instant.parse("2026-03-29T01:30:00Z"));
		// 01:30Z is 03:30 in Paris: its value is 53875, not the 54750 stale copy carried by the 02:30 phantom
		assertThat(snapshots).filteredOn(s -> s.observedAt().equals(Instant.parse("2026-03-29T01:30:00Z")))
				.extracting(GridSnapshot::consumption).containsExactly(53875);
	}

	@Test
	void definitiveYearsHaveTheSamePhantoms() {
		// 2025-03-30, checked on the real API: the phantoms are not a quirk of the consolidated data
		List<GridSnapshot> snapshots = lines("national-consdef-dst-spring-2025-definitive-trimmed.jsonl").stream()
				.map(NationalConsolidatedMapping::toSnapshot)
				.flatMap(Optional::stream)
				.toList();

		assertThat(snapshots).extracting(GridSnapshot::observedAt).containsExactlyInAnyOrder(
				Instant.parse("2025-03-30T00:00:00Z"),
				Instant.parse("2025-03-30T00:30:00Z"),
				Instant.parse("2025-03-30T01:00:00Z"),
				Instant.parse("2025-03-30T01:30:00Z"));
		assertThat(snapshots).allSatisfy(s -> assertThat(s.quality()).isEqualTo(Quality.DEFINITIVE));
		// The 02:30 phantom repeats the 00:30Z value (48415); the real 03:30 row is 48397
		assertThat(snapshots).filteredOn(s -> s.observedAt().equals(Instant.parse("2025-03-30T01:30:00Z")))
				.extracting(GridSnapshot::consumption).containsExactly(48397);
	}

	@Test
	void rowOutsideADstChangeIsKeptWhateverTheSeason() {
		// Summer time (UTC+2): 12:00Z is 14:00 in Paris. Winter time (UTC+1): 12:00Z is 13:00.
		assertThat(NationalConsolidatedMapping.toSnapshot(rowAt("2026-07-01T12:00:00Z", "14:00", "50000"))).isPresent();
		assertThat(NationalConsolidatedMapping.toSnapshot(rowAt("2026-01-15T12:00:00Z", "13:00", "50000"))).isPresent();
	}

	@Test
	void missingHeureCannotDisagreeSoTheRowIsKept() {
		assertThat(NationalConsolidatedMapping.toSnapshot(row(CONSOLIDATED, null, "49970"))).isPresent();
		assertThat(NationalConsolidatedMapping.toSnapshot(row(CONSOLIDATED, "not a time", "49970"))).isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "Données provisoires", "Données définitive" })
	void unknownNatureStopsTheBatch(String nature) {
		assertThatThrownBy(() -> NationalConsolidatedMapping.toSnapshot(row(nature, "01:00", "49970")))
				.isInstanceOf(OdreException.class)
				.extracting(e -> ((OdreException) e).reason()).isEqualTo(Reason.INVALID_RESPONSE);
	}

	@Test
	void positivePumpingIsDroppedButTheReadingIsKept() {
		NationalConsolidatedRecord row = new NationalConsolidatedRecord(Instant.parse("2026-07-01T12:00:00Z"), "14:00",
				CONSOLIDATED, "49970", "29", null, null, null, null, null, null, null, null, null, null, "350");

		GridSnapshot snapshot = NationalConsolidatedMapping.toSnapshot(row).orElseThrow();

		assertThat(snapshot.pumping()).isNull();
		assertThat(snapshot.consumption()).isEqualTo(49970);
	}

	@Test
	void lineDecodingIsStrictOnTheStructure() {
		assertThatThrownBy(() -> RESPONSES.nationalConsolidatedLine("")).isInstanceOf(OdreException.class);
		assertThatThrownBy(() -> RESPONSES.nationalConsolidatedLine("{not json")).isInstanceOf(OdreException.class);
		assertThatThrownBy(() -> RESPONSES.nationalConsolidatedLine("[1, 2]")).isInstanceOf(OdreException.class);
		assertThatThrownBy(() -> RESPONSES.nationalConsolidatedLine("{\"consommation\": 1}"))
				.isInstanceOf(OdreException.class);
	}

	private static List<NationalConsolidatedRecord> lines(String fixture) {
		return fixture(fixture).lines().map(RESPONSES::nationalConsolidatedLine).toList();
	}

	private static NationalConsolidatedRecord row(String nature, String heure, String consommation) {
		return new NationalConsolidatedRecord(Instant.parse("2026-01-15T00:00:00Z"), heure, nature, consommation,
				null, null, null, null, null, null, null, null, null, null, null, null);
	}

	private static NationalConsolidatedRecord rowAt(String dateHeure, String heure, String consommation) {
		return new NationalConsolidatedRecord(Instant.parse(dateHeure), heure, CONSOLIDATED, consommation,
				null, null, null, null, null, null, null, null, null, null, null, null);
	}
}
