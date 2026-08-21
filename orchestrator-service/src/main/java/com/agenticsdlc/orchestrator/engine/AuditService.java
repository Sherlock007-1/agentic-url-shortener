package com.agenticsdlc.orchestrator.engine;

import com.agenticsdlc.orchestrator.domain.AuditEvent;
import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.repository.AuditEventRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the append-only orchestration audit trail.
 *
 * <p>Joins the caller's transaction so an audit entry is never visible for a state
 * transition that was rolled back.
 */
@Service
public class AuditService {

	private final AuditEventRepository repository;
	private final Clock clock;

	public AuditService(AuditEventRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID workflowRunId, UUID taskId, AuditEventType type, String message, String details) {
		repository.save(new AuditEvent(workflowRunId, taskId, type, message, details, clock.instant()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID workflowRunId, UUID taskId, AuditEventType type, String message) {
		record(workflowRunId, taskId, type, message, null);
	}
}
