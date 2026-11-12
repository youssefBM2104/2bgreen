package com.twobgreen.platform.regional;

/**
 * A target was deleted (U13). Published in process for the modules that keep data about a target by its id: the rule
 * state of its pace alert lives in {@code automation} (W5, "Deleting data"). No module listens yet: the pace alert is
 * U16.
 */
public record TargetDeleted(long targetId) {
}
