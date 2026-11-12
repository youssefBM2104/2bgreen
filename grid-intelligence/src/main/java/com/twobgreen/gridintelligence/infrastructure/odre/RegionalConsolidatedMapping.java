package com.twobgreen.gridintelligence.infrastructure.odre;

import static com.twobgreen.gridintelligence.infrastructure.odre.OdreNumbers.wholeNumber;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twobgreen.gridintelligence.domain.GridSnapshot;
import com.twobgreen.gridintelligence.domain.Production;

/**
 * Anti-corruption layer for {@code eco2mix-regional-cons-def} (regional batch, G6), see
 * {@code docs/data/eco2mix-regional-cons-def.md}. The rules of the two ACLs it sits between:
 * <ul>
 * <li>like {@link RegionalRealtimeMapping}: scope {@code region:<code_insee_region>}, fossil production as the
 * aggregate {@code thermal}, no CO2 (estimated later by the domain, G7) and no forecasts;</li>
 * <li>like {@link NationalConsolidatedMapping}: quality from {@code nature}, and the phantom rows of the spring DST
 * change are dropped (same phantoms here: on 2025-03-30, {@code 01:00Z} comes as {@code 02:00} and {@code 03:00}).</li>
 * </ul>
 * A row without measured consumption or without region code is dropped too. Any other missing value, sentinels
 * ({@code "ND"}, {@code "-"}) included, only empties its field.
 */
final class RegionalConsolidatedMapping {

	private static final Logger log = LoggerFactory.getLogger(RegionalConsolidatedMapping.class);

	private RegionalConsolidatedMapping() {
	}

	/**
	 * @throws com.twobgreen.gridintelligence.application.OdreException if {@code nature} is unknown, as in the
	 *         national batch
	 */
	static Optional<GridSnapshot> toSnapshot(RegionalConsolidatedRecord row) {
		Integer consumption = wholeNumber("consommation", row.consommation());
		if (consumption == null) {
			return Optional.empty();
		}
		String code = row.codeInseeRegion() == null ? "" : row.codeInseeRegion().strip();
		if (code.isEmpty()) {
			log.warn("ODRÉ regional cons-def row at {} has no code_insee_region, dropped", row.dateHeure());
			return Optional.empty();
		}
		if (NationalConsolidatedMapping.isDaylightSavingPhantom(row.dateHeure(), row.heure())) {
			return Optional.empty();
		}
		Production production = new Production(
				wholeNumber("nucleaire", row.nucleaire()),
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
				NationalConsolidatedMapping.quality(row.nature(), "at " + row.dateHeure() + " for region " + code),
				false));
	}

	/** Zero or negative (K1). A positive value only loses this field. */
	private static Integer pumping(RegionalConsolidatedRecord row) {
		Integer pumping = wholeNumber("pompage", row.pompage());
		if (pumping != null && pumping > 0) {
			log.warn("ODRÉ regional cons-def pompage {} at {} ({}) is positive, treated as missing", pumping,
					row.dateHeure(), row.codeInseeRegion());
			return null;
		}
		return pumping;
	}
}
