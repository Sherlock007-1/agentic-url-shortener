package com.agenticsdlc.orchestrator.graph;

import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;

/**
 * The SDLC workflow template used for graph version 1.
 *
 * <pre>
 * requirement-analysis -> codebase-analysis -> planning -> architecture -> implementation
 *                                                                              |
 *                                              +-------------+-----------------+
 *                                              v             v                 v
 *                                            tests       security        documentation
 *                                              +-------------+-----------------+
 *                                                            v
 *                                                        validation   (join)
 * </pre>
 *
 * <p>The template only declares nodes and edges; it is persisted per workflow so
 * the engine schedules from database state rather than from this class.
 */
public final class SdlcWorkflowGraphTemplate {

	public static final int VERSION = 1;
	public static final String DESCRIPTION = "Baseline SDLC workflow";

	public static final String REQUIREMENT_ANALYSIS = "requirement-analysis";
	public static final String CODEBASE_ANALYSIS = "codebase-analysis";
	public static final String PLANNING = "planning";
	public static final String ARCHITECTURE = "architecture";
	public static final String IMPLEMENTATION = "implementation";
	public static final String TESTS = "tests";
	public static final String SECURITY = "security";
	public static final String DOCUMENTATION = "documentation";
	public static final String VALIDATION = "validation";

	private static final List<TaskDefinition> TASKS = List.of(
			new TaskDefinition(REQUIREMENT_ANALYSIS, "Requirement Analysis", AgentType.REQUIREMENT, List.of()),
			new TaskDefinition(CODEBASE_ANALYSIS, "Codebase Analysis", AgentType.CODEBASE_ANALYSIS,
					List.of(REQUIREMENT_ANALYSIS)),
			new TaskDefinition(PLANNING, "Planning", AgentType.PLANNING, List.of(CODEBASE_ANALYSIS)),
			new TaskDefinition(ARCHITECTURE, "Architecture", AgentType.ARCHITECTURE, List.of(PLANNING)),
			new TaskDefinition(IMPLEMENTATION, "Implementation", AgentType.IMPLEMENTATION, List.of(ARCHITECTURE)),
			new TaskDefinition(TESTS, "Tests", AgentType.TEST, List.of(IMPLEMENTATION)),
			new TaskDefinition(SECURITY, "Security Review", AgentType.SECURITY_RISK, List.of(IMPLEMENTATION)),
			new TaskDefinition(DOCUMENTATION, "Documentation", AgentType.DOCUMENTATION, List.of(IMPLEMENTATION)),
			new TaskDefinition(VALIDATION, "Validation", AgentType.VALIDATION,
					List.of(TESTS, SECURITY, DOCUMENTATION)));

	private SdlcWorkflowGraphTemplate() {
	}

	/** Nodes in topological (declaration) order. */
	public static List<TaskDefinition> tasks() {
		return TASKS;
	}
}
