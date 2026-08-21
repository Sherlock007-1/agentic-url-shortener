package com.agenticsdlc.orchestrator.scenario;

/** Which kind of engineering problem a scenario represents. */
public enum ScenarioType {

	/** A capability that did not exist in the baseline at all. */
	GREENFIELD,

	/** A change to behaviour that already existed. */
	BROWNFIELD,

	/** A requirement that cannot be actioned as written. */
	AMBIGUOUS
}
