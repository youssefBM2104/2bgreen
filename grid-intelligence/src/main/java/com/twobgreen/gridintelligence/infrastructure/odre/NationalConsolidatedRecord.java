package com.twobgreen.gridintelligence.infrastructure.odre;

import java.time.Instant;

/**
 * One row of {@code eco2mix-national-cons-def}, named as ODRÉ names it. Same raw-text convention as
 * {@link NationalRealtimeRecord}: deciding what is a number belongs to the mapping.
 * <p>
 * Differences with the real-time dataset: {@code nature} is a real quality marker, and there is no onshore/offshore
 * wind split and no battery field. {@code heure} (Paris local time) is decoded only to spot the phantom rows of the
 * spring DST change, see {@link NationalConsolidatedMapping}; it is never used as a timestamp.
 *
 * @param dateHeure {@code date_heure}, UTC start of the period, the only timestamp to use
 * @param heure {@code heure}, Paris local {@code HH:mm}, or {@code null} when absent
 * @param nature {@code "Données consolidées"} or {@code "Données définitives"}
 */
record NationalConsolidatedRecord(
		Instant dateHeure,
		String heure,
		String nature,
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
