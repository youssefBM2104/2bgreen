package com.twobgreen.gridintelligence.infrastructure.odre;

import static com.twobgreen.gridintelligence.infrastructure.odre.OdreNumbers.decimal;
import static com.twobgreen.gridintelligence.infrastructure.odre.OdreNumbers.wholeNumber;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twobgreen.gridintelligence.application.OdreException;
import com.twobgreen.gridintelligence.application.OdreException.Reason;
import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;
import com.twobgreen.gridintelligence.domain.Quality;

/**
 * Anti-corruption layer for {@code eco2mix-national-cons-def} (batch layer, G3). Rules come from the checks on the
 * dataset, see {@code docs/data/eco2mix-national-cons-def.md}.
 * <p>
 * A row is dropped, not turned into a snapshot, in two cases:
 * <ul>
 * <li><b>Placeholder.</b> The dataset is declared on a 15-min grid but its real step is 30 min: the {@code :15} and
 * {@code :45} rows carry no consumption, like the forecast tail of {@code tr}.</li>
 * <li><b>Phantom of the spring DST change.</b> On the day clocks go forward, Paris skips 02:00 to 03:00, yet the
 * consolidated data holds rows labelled {@code 02:xx} <em>and</em> {@code 03:xx} for the same {@code date_heure}
 * (2026-03-29: four extra rows, the {@code 02:30} one carrying the value of {@code 01:30}). The phantom is the
 * row whose {@code heure} disagrees with its own {@code date_heure} converted to Paris time. Keeping both would
 * give two rows for one {@code (scope, observed_at)}, which an upsert cannot absorb.</li>
 * </ul>
 * {@code heure} is therefore read for that one check only. It never builds a timestamp: {@code date_heure} (UTC) does.
 */
final class NationalConsolidatedMapping {

	private static final Logger log = LoggerFactory.getLogger(NationalConsolidatedMapping.class);

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

	private static final String CONSOLIDATED = "Données consolidées";

	private static final String DEFINITIVE = "Données définitives";

	private NationalConsolidatedMapping() {
	}

	/**
	 * Empty for a placeholder or a DST phantom, see the class comment. Any other missing value only empties its
	 * field.
	 *
	 * @throws OdreException {@link Reason#INVALID_RESPONSE} if {@code nature} is none of the two known values: a
	 *         new quality would be stored wrongly, so a format change has to stop the batch rather than slip through
	 */
	static Optional<GridSnapshot> toSnapshot(NationalConsolidatedRecord row) {
		Integer consumption = wholeNumber("consommation", row.consommation());
		if (consumption == null) {
			return Optional.empty();
		}
		if (isDaylightSavingPhantom(row.dateHeure(), row.heure())) {
			log.debug("ODRÉ cons-def row at {} labelled {} is a DST phantom, dropped", row.dateHeure(), row.heure());
			return Optional.empty();
		}
		Production production = new Production(
				wholeNumber("nucleaire", row.nucleaire()),
				wholeNumber("gaz", row.gaz()),
				wholeNumber("charbon", row.charbon()),
				wholeNumber("fioul", row.fioul()),
				// National data splits fossil fuels, so there is no aggregate
				null,
				// Total only: this dataset has no onshore/offshore split
				wholeNumber("eolien", row.eolien()),
				wholeNumber("solaire", row.solaire()),
				wholeNumber("hydraulique", row.hydraulique()),
				wholeNumber("bioenergies", row.bioenergies()));
		return Optional.of(new GridSnapshot(
				GridSnapshot.NATIONAL_SCOPE,
				row.dateHeure(),
				decimal("taux_co2", row.tauxCo2()),
				consumption,
				wholeNumber("prevision_j", row.previsionJ()),
				wholeNumber("prevision_j1", row.previsionJ1()),
				production,
				pumping(row),
				quality(row),
				false));
	}

	/**
	 * Does {@code heure} disagree with {@code date_heure} read in Paris time? A missing or unreadable {@code heure}
	 * cannot disagree: the row is kept, since dropping it on a doubt would open a hole. Shared with the regional
	 * cons-def, which has the same phantoms (checked on 2025-03-30).
	 */
	static boolean isDaylightSavingPhantom(Instant dateHeure, String heure) {
		if (heure == null) {
			return false;
		}
		LocalTime label;
		try {
			label = LocalTime.parse(heure.strip());
		}
		catch (DateTimeParseException e) {
			log.warn("ODRÉ cons-def heure \"{}\" at {} is not HH:mm, DST check skipped", heure, dateHeure);
			return false;
		}
		LocalTime paris = dateHeure.atZone(PARIS).toLocalTime().truncatedTo(ChronoUnit.MINUTES);
		return !paris.equals(label);
	}

	private static Quality quality(NationalConsolidatedRecord row) {
		return quality(row.nature(), "at " + row.dateHeure());
	}

	/**
	 * Quality from ODRÉ's {@code nature}.
	 *
	 * @param where what to put in the message to find the row, e.g. a timestamp: the value itself is not logged
	 */
	static Quality quality(String nature, String where) {
		return switch (nature == null ? "" : nature.strip()) {
			case CONSOLIDATED -> Quality.CONSOLIDATED;
			case DEFINITIVE -> Quality.DEFINITIVE;
			default -> throw new OdreException(Reason.INVALID_RESPONSE,
					"unknown nature " + where + ": expected consolidated or definitive data");
		};
	}

	/** Zero or negative (K1). A positive value only loses this field, not the whole reading. */
	private static Integer pumping(NationalConsolidatedRecord row) {
		Integer pumping = wholeNumber("pompage", row.pompage());
		if (pumping != null && pumping > 0) {
			log.warn("ODRÉ cons-def pompage {} at {} is positive, treated as missing (zero or negative expected)",
					pumping, row.dateHeure());
			return null;
		}
		return pumping;
	}
}
