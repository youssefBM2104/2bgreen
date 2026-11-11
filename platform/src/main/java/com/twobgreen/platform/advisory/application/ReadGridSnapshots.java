package com.twobgreen.platform.advisory.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.twobgreen.platform.advisory.GridScope;
import com.twobgreen.platform.advisory.GridSnapshot;
import com.twobgreen.platform.advisory.GridSnapshots;
import com.twobgreen.platform.advisory.domain.GridMix;
import com.twobgreen.platform.advisory.domain.GridReading;

/** {@link GridSnapshots} for the other modules: the readings of {@link GridReadings}, as they are. */
public class ReadGridSnapshots implements GridSnapshots {

	private final GridReadings readings;

	public ReadGridSnapshots(GridReadings readings) {
		this.readings = readings;
	}

	@Override
	public Optional<GridSnapshot> latest(GridScope scope) {
		return readings.latest(scope).map(reading -> published(scope, reading));
	}

	@Override
	public List<GridSnapshot> between(GridScope scope, Instant from, Instant to) {
		return readings.between(scope, from, to).stream().map(reading -> published(scope, reading)).toList();
	}

	private static GridSnapshot published(GridScope scope, GridReading reading) {
		GridMix mix = reading.mix();
		return new GridSnapshot(scope, reading.observedAt(), mix.consumption(),
				new GridSnapshot.Production(mix.nuclear(), mix.gas(), mix.coal(), mix.oil(), mix.thermal(), mix.wind(),
						mix.solar(), mix.hydro(), mix.bioenergy()),
				reading.co2Intensity(), reading.estimated(), reading.quality().name(), reading.storedAt());
	}
}
