package com.twobgreen.gridintelligence.infrastructure.odre;

import java.util.List;

/**
 * One response of the Explore v2.1 {@code records} endpoint: {@code {"total_count": ..., "results": [...]}}.
 *
 * @param totalCount number of rows matching the query, all pages included
 */
record RecordsPage<T>(long totalCount, List<T> results) {
}
