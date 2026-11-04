package com.twobgreen.platform.advisory.domain;

/** How reliable a reading is, as published by the read API of grid-intelligence (contract K2). */
public enum GridQuality {
	PROVISIONAL,
	CONSOLIDATED,
	DEFINITIVE
}
