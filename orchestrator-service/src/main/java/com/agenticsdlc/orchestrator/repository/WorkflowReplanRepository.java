package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.WorkflowReplan;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowReplanRepository extends JpaRepository<WorkflowReplan, UUID> {

	List<WorkflowReplan> findByWorkflowRunIdOrderByCreatedAtAsc(UUID workflowRunId);
}
