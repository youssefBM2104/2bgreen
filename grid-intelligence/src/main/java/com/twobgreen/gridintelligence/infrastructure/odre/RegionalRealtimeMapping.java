package com.twobgreen.gridintelligence.infrastructure.odre;

import static com.twobgreen.gridintelligence.infrastructure.odre.OdreNumbers.wholeNumber;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * Anti-corruption layer for {@code eco2mix-regional-tr}, see {@code docs/data/eco2mix-regional-tr.md}. Same
 * defensive rules as the national one; what differs is the dataset:
 * <ul>
 * <li>scope is {@code region:<code_insee_region>};</li>
 * <li>fossil production is the single aggregate {@code thermique}, which goes to {@code production.thermal}
 * (gas, coal and oil stay missing);</li>
 * <li>no CO2 and no forecasts exist, so those fields stay missing: no CO2 is invented here (regional CO2 is G7,
 * and {@code estimated} stays false until a CO2 value is actually estimated).</li>
 * </ul>
 */
final class RegionalRealtimeMapping {

	private static final Logger log = LoggerFactory.getLogger(RegionalRealtimeMapping.class);

	private RegionalRealtimeMapping() {
	}

	/**
	 * Empty for a row without measured consumption (a period ODRÉ has not filled in yet, K1 section 5), and for a row
	 * without region code, which cannot be attributed to any scope.
	 */
	static Optional<GridSnapshot> toSnapshot(RegionalRealtimeRecord row) {
		Integer consumption = wholeNumber("consommation", row.consommation());
		if (consumption == null) {
			return Optional.empty();
		}
		String code = row.codeInseeRegion() == null ? "" : row.codeInseeRegion().strip();
		if (code.isEmpty()) {
			log.warn("ODRÉ regional row at {} has no code_insee_region, dropped", row.dateHeure());
			return Optional.empty();
		}
		Production production = new Production(
				wholeNumber("nucleaire", row.nucleaire()),
				// Regional data has no gas/coal/oil split: only the aggregate below
				null,
				null,
				null,
				wholeNumber("thermique", row.thermique()),
				wholeNumber("eolien", row.eolien()),
				wholeNumber("solaire", row.solaire()),
				wholeNumber("hydraulique", row.hydraulique()),
				wholeNumber("bioenergies", row.bioenergies()));
		return Optional.of(new GridSnapshot(
				GridSnapshot.regionScope(code),
				row.dateHeure(),
				null,
				consumption,
				null,
				null,
				production,
				pumping(row),
				Quality.PROVISIONAL,
				false));
	}

	/** Zero or negative (K1). A positive value only loses this field, not the whole reading. */
	private static Integer pumping(RegionalRealtimeRecord row) {
		Integer pumping = wholeNumber("pompage", row.pompage());
		if (pumping != null && pumping > 0) {
			log.warn("ODRÉ regional pompage {} at {} ({}) is positive, treated as missing", pumping, row.dateHeure(),
					row.codeInseeRegion());
			return null;
		}
		return pumping;
	}
}
