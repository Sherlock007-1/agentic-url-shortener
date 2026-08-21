package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.Decision;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

	List<Decision> findByWorkflowRunIdOrderByCreatedAtAsc(UUID workflowRunId);
}
