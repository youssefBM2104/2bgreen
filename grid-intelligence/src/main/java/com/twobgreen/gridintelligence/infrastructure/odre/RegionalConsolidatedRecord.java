package com.twobgreen.gridintelligence.infrastructure.odre;

import java.time.Instant;

/**
 * One row of {@code eco2mix-regional-cons-def}, named as ODRÉ names it: one row per region and period. Only the
 * columns of the snapshot are fetched (the export asks for them with {@code select}): the {@code tco_*} / {@code tch_*}
 * rates, the exchanges, the battery fields and the onshore/offshore split stay at ODRÉ. Measures keep the raw text, as
 * in the other records.
 *
 * @param dateHeure {@code date_heure}, UTC start of the period, the only timestamp to use
 * @param heure {@code heure}, Paris local {@code HH:mm}: only read to spot the DST phantoms
 * @param nature {@code "Données consolidées"} or {@code "Données définitives"}
 * @param thermique fossil production, one aggregate (no gas/coal/oil split in regional data)
 */
record RegionalConsolidatedRecord(
		Instant dateHeure,
		String heure,
		String nature,
		String codeInseeRegion,
		String consommation,
		String thermique,
		String nucleaire,
		String eolien,
		String solaire,
		String hydraulique,
		String bioenergies,
		String pompage) {
}
