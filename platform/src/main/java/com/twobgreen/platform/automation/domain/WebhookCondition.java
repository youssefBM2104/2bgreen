package com.twobgreen.platform.automation.domain;

/** When a webhook fires (U9). */
public enum WebhookCondition {

	/** CO2 goes below the threshold. */
	BELOW,

	/** CO2 goes above the threshold. */
	ABOVE,

	/** The traffic-light colour changes. No threshold. */
	BAND_CHANGED
}
