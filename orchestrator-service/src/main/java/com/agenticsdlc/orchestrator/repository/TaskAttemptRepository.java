package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.TaskAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAttemptRepository extends JpaRepository<TaskAttempt, UUID> {

	List<TaskAttempt> findByTaskIdOrderByAttemptNoAsc(UUID taskId);

	List<TaskAttempt> findByWorkflowRunIdOrderByStartedAtAsc(UUID workflowRunId);

	/** Retries are every agent/task attempt after the first one. */
	long countByAttemptNoGreaterThan(int attemptNo);
}
