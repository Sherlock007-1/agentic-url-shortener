package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.AgentExecution;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentExecutionRepository extends JpaRepository<AgentExecution, UUID> {

	List<AgentExecution> findByWorkflowRunIdOrderByStartedAtAsc(UUID workflowRunId);

	List<AgentExecution> findByTaskId(UUID taskId);
}
