package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.WorkflowGraphVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowGraphVersionRepository extends JpaRepository<WorkflowGraphVersion, UUID> {

	Optional<WorkflowGraphVersion> findByWorkflowRunIdAndVersion(UUID workflowRunId, int version);
}
