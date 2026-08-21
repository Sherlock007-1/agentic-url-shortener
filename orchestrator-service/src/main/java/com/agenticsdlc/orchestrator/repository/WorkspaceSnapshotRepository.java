package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.RollbackStatus;
import com.agenticsdlc.orchestrator.domain.WorkspaceSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceSnapshotRepository extends JpaRepository<WorkspaceSnapshot, UUID> {

	List<WorkspaceSnapshot> findByWorkflowRunIdOrderByCreatedAtAsc(UUID workflowRunId);

	long countByRollbackStatus(RollbackStatus rollbackStatus);
}
