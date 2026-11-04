package com.twobgreen.gridintelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

class GetLatestSnapshotTests {

	private static final StoredSnapshot STORED = new StoredSnapshot(
			new GridSnapshot("national", Instant.parse("2026-10-20T14:45:00Z"), null, 49970, null, null, Production.NONE,
					null, Quality.PROVISIONAL, false),
			Instant.parse("2026-10-20T15:03:12Z"));

	@Test
	void returnsWhatTheReaderFinds() {
		GetLatestSnapshot useCase = new GetLatestSnapshot(scope -> Optional.of(STORED));

		assertThat(useCase.latest("national")).isSameAs(STORED);
	}

	@Test
	void nothingStoredIsAnErrorOfItsOwn() {
		GetLatestSnapshot useCase = new GetLatestSnapshot(scope -> Optional.empty());

		assertThatThrownBy(() -> useCase.latest("national"))
				.isInstanceOf(NoSnapshotException.class)
				.hasMessageContaining("national");
	}

	@Test
	void asksTheReaderForTheRequestedScope() {
		String[] asked = new String[1];
		GetLatestSnapshot useCase = new GetLatestSnapshot(scope -> {
			asked[0] = scope;
			return Optional.of(STORED);
		});

		useCase.latest("national");

		assertThat(asked[0]).isEqualTo("national");
	}
}
