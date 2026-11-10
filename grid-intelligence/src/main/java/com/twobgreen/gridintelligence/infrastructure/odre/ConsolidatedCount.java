package com.twobgreen.gridintelligence.infrastructure.odre;

/**
 * One group of the change-detection query on {@code eco2mix-national-cons-def}: how many rows a UTC year holds with a
 * given {@code nature}. A year can appear twice, because quality flips on Paris years while this counts UTC years
 * (2025 holds 4 consolidated rows: Paris 2026-01-01 00:00 to 00:45).
 *
 * @param year UTC year of {@code date_heure}
 * @param nature as ODRÉ writes it, translated by the mapping
 * @param rows number of rows, placeholders included
 */
record ConsolidatedCount(int year, String nature, long rows) {
}
