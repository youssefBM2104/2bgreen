package com.twobgreen.gridintelligence.application;

import java.util.List;

import com.twobgreen.gridintelligence.domain.GridSnapshot;

/**
 * Port to the batch layer table. The batch is its only writer. The table can be rebuilt from ODRÉ at any time.
 */
public interface BatchSnapshotRepository {

	/**
	 * Inserts each snapshot, or updates the existing row with the same ({@code scope}, {@code observedAt}). A row is
	 * left untouched when:
	 * <ul>
	 * <li>the stored quality is higher than the incoming one (DEFINITIVE is never replaced by CONSOLIDATED), or</li>
	 * <li>nothing differs, so a second run over the same data writes nothing, {@code stored_at} included.</li>
	 * </ul>
	 * Idempotent, and safe to repeat after a crash.
	 *
	 * @return how many rows were inserted or updated
	 */
	int upsert(List<GridSnapshot> snapshots);
}
