package com.agenticsdlc.orchestrator;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the agentic SDLC orchestrator service.
 *
 * <p>Orchestration behaviour (persisted DAG, gates, agents, approvals, metrics)
 * is implemented incrementally; this class only bootstraps the application.
 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Agentic SDLC Orchestrator API", version = "v1",
		description = "Requirement intake, persisted DAG execution, decisions and audit trail"))
public class OrchestratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrchestratorApplication.class, args);
	}
}