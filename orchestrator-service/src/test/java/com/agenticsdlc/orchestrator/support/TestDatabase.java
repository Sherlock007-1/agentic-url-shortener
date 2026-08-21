package com.agenticsdlc.orchestrator.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test helper that resets the orchestration tables between test methods.
 *
 * <p>Uses DELETE rather than TRUNCATE: it respects the foreign keys via ordering
 * and does not require an exclusive table lock.
 */
public final class TestDatabase {

	private static final String[] TABLES = {
			"orchestrator.audit_events",
			"orchestrator.decisions",
			"orchestrator.agent_executions",
			"orchestrator.task_dependencies",
			"orchestrator.workflow_tasks",
			"orchestrator.workflow_graph_versions",
			"orchestrator.workflow_runs",
			"orchestrator.requirements"
	};

	private TestDatabase() {
	}

	public static void clean(JdbcTemplate jdbcTemplate) {
		for (String table : TABLES) {
			jdbcTemplate.update("DELETE FROM " + table);
		}
	}
}