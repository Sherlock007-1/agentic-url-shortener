package com.agenticsdlc.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the agentic SDLC orchestrator service.
 *
 * <p>Orchestration behaviour (persisted DAG, gates, agents, approvals, metrics)
 * is implemented incrementally; this class only bootstraps the application.
 */
@SpringBootApplication
public class OrchestratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrchestratorApplication.class, args);
	}
}
