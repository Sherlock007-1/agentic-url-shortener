package com.agenticsdlc.orchestrator.engine;

import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety-net poller for the single orchestrator instance.
 *
 * <p>Workflows are normally driven forward by the start API and by task
 * completions; this poller additionally picks up runs that were left RUNNING (for
 * example after a restart) so progress never depends on in-memory state alone.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduler {

	private static final Logger log = LoggerFactory.getLogger(WorkflowScheduler.class);

	private final WorkflowRunRepository workflowRunRepository;
	private final WorkflowEngine engine;

	public WorkflowScheduler(WorkflowRunRepository workflowRunRepository, WorkflowEngine engine) {
		this.workflowRunRepository = workflowRunRepository;
		this.engine = engine;
	}

	@Scheduled(fixedDelayString = "${orchestrator.poll-interval-ms:500}")
	public void pollRunningWorkflows() {
		for (UUID workflowRunId : runningWorkflowIds()) {
			try {
				engine.advance(workflowRunId);
			}
			catch (DataAccessException ex) {
				// Rows changed underneath the poller; the next tick picks them up again.
				log.debug("Polling skipped workflow {}: {}", workflowRunId, ex.getMessage());
			}
			catch (RuntimeException ex) {
				log.error("Polling failed for workflow {}", workflowRunId, ex);
			}
		}
	}

	private List<UUID> runningWorkflowIds() {
		return workflowRunRepository.findByStatusIn(List.of(WorkflowStatus.RUNNING)).stream()
				.map(WorkflowRun::getId)
				.toList();
	}
}
