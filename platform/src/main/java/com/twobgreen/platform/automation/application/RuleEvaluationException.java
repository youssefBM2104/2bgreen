package com.twobgreen.platform.automation.application;

/** Some subjects could not be evaluated for a snapshot. The snapshot has to be handed over again. */
public class RuleEvaluationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RuleEvaluationException(String message) {
		super(message);
	}
}
