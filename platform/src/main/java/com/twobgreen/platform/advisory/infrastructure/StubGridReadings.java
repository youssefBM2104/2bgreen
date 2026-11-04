package com.twobgreen.platform.advisory.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.twobgreen.platform.advisory.application.GridReadings;
import com.twobgreen.platform.advisory.domain.GridReading;

/**
 * {@link GridReadings} on a fixed answer, to work without grid-intelligence, Kafka and its data. It always returns
 * the K2 example {@code docs/api/examples/latest-national.json}, which the build puts on the classpath (no copy in
 * the repo), so its date is old and never moves.
 */
class StubGridReadings implements GridReadings {

	private static final Logger log = LoggerFactory.getLogger(StubGridReadings.class);

	private static final String EXAMPLE = "k2-examples/latest-national.json";

	private final GridReading reading;

	StubGridReadings(GridReading reading) {
		this.reading = reading;
	}

	static StubGridReadings fromContractExample() {
		try (InputStream in = new ClassPathResource(EXAMPLE).getInputStream()) {
			GridReading reading = GridReadingJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			log.warn("grid.client.mode=stub: grid data is FAKE, every reading is the K2 example observed at {}",
					reading.observedAt());
			return new StubGridReadings(reading);
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot read " + EXAMPLE + " from the classpath", e);
		}
	}

	@Override
	public Optional<GridReading> latestNational() {
		return Optional.of(reading);
	}
}
