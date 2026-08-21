package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.Approval;
import com.agenticsdlc.orchestrator.domain.ApprovalGate;
import com.agenticsdlc.orchestrator.domain.ApprovalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, UUID> {

	List<Approval> findByWorkflowRunIdOrderByRequestedAtAsc(UUID workflowRunId);

	Optional<Approval> findByWorkflowRunIdAndGateAndGraphVersion(UUID workflowRunId, ApprovalGate gate,
			int graphVersion);

	List<Approval> findByWorkflowRunIdAndStatus(UUID workflowRunId, ApprovalStatus status);

	long countByStatus(ApprovalStatus status);
}
