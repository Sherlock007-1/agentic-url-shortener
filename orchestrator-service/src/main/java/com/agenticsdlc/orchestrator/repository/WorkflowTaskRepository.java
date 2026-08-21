package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, UUID> {

	List<WorkflowTask> findByWorkflowRunIdOrderBySequenceNoAsc(UUID workflowRunId);

	List<WorkflowTask> findByGraphVersionIdOrderBySequenceNoAsc(UUID graphVersionId);

	Optional<WorkflowTask> findByWorkflowRunIdAndTaskKey(UUID workflowRunId, String taskKey);
}
