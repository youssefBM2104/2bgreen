package com.twobgreen.gridintelligence.domain;

/**
 * How reliable a reading is. Derived by our pipeline from the source, never read from ODRÉ's {@code nature} on
 * real-time data: PROVISIONAL comes from the speed layer, the other two from the batch layer.
 */
public enum Quality {
	PROVISIONAL,
	CONSOLIDATED,
	DEFINITIVE
}
