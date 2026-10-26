package com.twobgreen.gridintelligence.infrastructure.odre;

import java.time.Instant;

/**
 * One row of {@code eco2mix-national-tr}, named as ODRÉ names it. Only the fields of the K1 snapshot are kept, the
 * others are ignored when decoding.
 * <p>
 * Measures keep the raw text ODRÉ sent ({@code 29}, {@code "2458"}, {@code "ND"}...), or {@code null} when absent:
 * the declared types are not reliable (see {@code docs/data/}), so deciding what is a number and what means
 * "missing" belongs to the mapping to the domain (D4), not to the decoding.
 *
 * @param dateHeure {@code date_heure}, UTC start of the 15-min period, the only timestamp to use
 */
record NationalRealtimeRecord(
		Instant dateHeure,
		String consommation,
		String tauxCo2,
		String previsionJ,
		String previsionJ1,
		String nucleaire,
		String gaz,
		String charbon,
		String fioul,
		String eolien,
		String solaire,
		String hydraulique,
		String bioenergies,
		String pompage) {
}
