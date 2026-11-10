package com.twobgreen.gridintelligence.infrastructure.odre;

import java.time.Instant;

/**
 * One row of {@code eco2mix-regional-tr}, named as ODRÉ names it: one row per region and period. Only the fields of
 * the K1 snapshot are kept; {@code tco_*}, {@code tch_*}, {@code ech_physiques}, the battery fields and the trailing
 * {@code column_N} artifact are ignored when decoding. There is no CO2 and no forecast in this dataset.
 * <p>
 * Measures keep the raw text ODRÉ sent, like {@link NationalRealtimeRecord}: the mapping decides what is a number.
 *
 * @param dateHeure {@code date_heure}, UTC start of the 15-min period, the only timestamp to use
 * @param codeInseeRegion {@code code_insee_region}, e.g. {@code "32"}; the region's identity
 * @param thermique fossil production, one aggregate (no gas/coal/oil split in regional data)
 */
record RegionalRealtimeRecord(
		Instant dateHeure,
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
