package com.twobgreen.gridintelligence.infrastructure.odre;

import static com.twobgreen.gridintelligence.infrastructure.odre.OdreNumbers.decimal;
import static com.twobgreen.gridintelligence.infrastructure.odre.OdreNumbers.wholeNumber;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * Anti-corruption layer for {@code eco2mix-national-tr}: the only place where ODRÉ field names meet the domain
 * (contract K1). Rules come from the checks on the dataset, see {@code docs/data/eco2mix-national-tr.md}.
 * <p>
 * Only {@code date_heure} (UTC) is used as timestamp: {@code date} and {@code heure} are Paris local time and are not
 * even decoded.
 */
final class NationalRealtimeMapping {

	static final String SCOPE = "national";

	private static final Logger log = LoggerFactory.getLogger(NationalRealtimeMapping.class);

	private NationalRealtimeMapping() {
	}

	/**
	 * Empty for a row without measured consumption, which covers both shapes of the forecast tail (rest of today,
	 * tomorrow): such a row never becomes a snapshot (K1 section 5). Any other missing value only empties its field.
	 */
	static Optional<GridSnapshot> toSnapshot(NationalRealtimeRecord row) {
		Integer consumption = wholeNumber("consommation", row.consommation());
		if (consumption == null) {
			return Optional.empty();
		}
		Production production = new Production(
				wholeNumber("nucleaire", row.nucleaire()),
				wholeNumber("gaz", row.gaz()),
				wholeNumber("charbon", row.charbon()),
				wholeNumber("fioul", row.fioul()),
				// National data splits fossil fuels, so there is no aggregate
				null,
				wholeNumber("eolien", row.eolien()),
				wholeNumber("solaire", row.solaire()),
				wholeNumber("hydraulique", row.hydraulique()),
				wholeNumber("bioenergies", row.bioenergies()));
		return Optional.of(new GridSnapshot(
				SCOPE,
				row.dateHeure(),
				decimal("taux_co2", row.tauxCo2()),
				consumption,
				wholeNumber("prevision_j", row.previsionJ()),
				wholeNumber("prevision_j1", row.previsionJ1()),
				production,
				pumping(row),
				Quality.PROVISIONAL,
				false));
	}

	/**
	 * Zero or negative (MW drawn from the grid). A positive value breaks K1, so only this field is dropped: losing the
	 * whole reading would leave a hole, the poller does not fetch a period twice.
	 */
	private static Integer pumping(NationalRealtimeRecord row) {
		Integer pumping = wholeNumber("pompage", row.pompage());
		if (pumping != null && pumping > 0) {
			log.warn("ODRÉ pompage {} at {} is positive, treated as missing (zero or negative expected)",
					pumping, row.dateHeure());
			return null;
		}
		return pumping;
	}
}
