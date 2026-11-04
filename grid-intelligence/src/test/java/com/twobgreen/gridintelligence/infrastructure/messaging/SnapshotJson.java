package com.twobgreen.gridintelligence.infrastructure.messaging;

/**
 * K1 messages used by the stream tests. The first one carries the values of the committed fixture
 * {@code national-tr-measured.json} (2026-10-20T14:45Z); the second shows missing values.
 */
public final class SnapshotJson {

	public static final String NATIONAL_MEASURED = """
			{
			  "schemaVersion": 1,
			  "scope": "national",
			  "observedAt": "2026-10-20T14:45:00Z",
			  "co2Intensity": 29,
			  "consumption": 49970,
			  "consumptionForecastDay": 49950,
			  "consumptionForecastDayBefore": 49650,
			  "production": {
			    "nuclear": 38499,
			    "gas": 2632,
			    "coal": 0,
			    "oil": 37,
			    "thermal": null,
			    "wind": 6225,
			    "solar": 4656,
			    "hydro": 3768,
			    "bioenergy": 941
			  },
			  "pumping": 0,
			  "quality": "PROVISIONAL",
			  "estimated": false
			}
			""";

	/** Regional style: no CO2, no forecasts, fossil aggregate only, several fields absent instead of null. */
	public static final String REGION_MISSING_VALUES = """
			{
			  "schemaVersion": 1,
			  "scope": "region:76",
			  "observedAt": "2026-10-20T14:45:00Z",
			  "consumption": 3805,
			  "consumptionForecastDay": null,
			  "production": {
			    "nuclear": 1257,
			    "thermal": 1,
			    "wind": 376,
			    "hydro": null
			  },
			  "quality": "PROVISIONAL",
			  "estimated": true
			}
			""";

	private SnapshotJson() {
	}
}
