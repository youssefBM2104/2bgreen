package com.twobgreen.platform.regional.domain;

import java.util.Map;
import java.util.Optional;

/**
 * The regions an official can be attached to: the 12 regions of {@code eco2mix-regional-tr}, by INSEE code (list
 * read on ODRÉ on 2026-11-11, no Corse). The same codes are the only values the Keycloak attribute {@code region_code}
 * accepts.
 */
public final class Regions {

	private static final Map<String, String> NAMES = Map.ofEntries(
			Map.entry("11", "Île-de-France"),
			Map.entry("24", "Centre-Val de Loire"),
			Map.entry("27", "Bourgogne-Franche-Comté"),
			Map.entry("28", "Normandie"),
			Map.entry("32", "Hauts-de-France"),
			Map.entry("44", "Grand Est"),
			Map.entry("52", "Pays de la Loire"),
			Map.entry("53", "Bretagne"),
			Map.entry("75", "Nouvelle-Aquitaine"),
			Map.entry("76", "Occitanie"),
			Map.entry("84", "Auvergne-Rhône-Alpes"),
			Map.entry("93", "Provence-Alpes-Côte d'Azur"));

	private Regions() {
	}

	/** The name of a known region, empty for any other value (null included). */
	public static Optional<String> nameOf(String code) {
		return Optional.ofNullable(code).map(NAMES::get);
	}
}
