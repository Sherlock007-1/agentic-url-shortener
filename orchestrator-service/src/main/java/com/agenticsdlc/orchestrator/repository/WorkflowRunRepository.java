package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {

	List<WorkflowRun> findByStatusIn(Collection<WorkflowStatus> statuses);

	long countByStatus(WorkflowStatus status);
}