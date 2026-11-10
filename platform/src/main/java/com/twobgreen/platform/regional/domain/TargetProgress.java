package com.twobgreen.platform.regional.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Value and status of a target on one day (U13). Inventory row 11. One row per target and day, deleted with the
 * target by the database.
 *
 * @param id null until stored
 */
@Table("regional_target_progress")
public record TargetProgress(@Id Long id, long targetId, LocalDate day, BigDecimal value, TargetStatus status) {
}
