package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.ClarificationRequest;
import com.agenticsdlc.orchestrator.domain.ClarificationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClarificationRequestRepository extends JpaRepository<ClarificationRequest, UUID> {

	List<ClarificationRequest> findByWorkflowRunIdOrderByRequestedAtAsc(UUID workflowRunId);

	List<ClarificationRequest> findByWorkflowRunIdAndStatus(UUID workflowRunId, ClarificationStatus status);
}
