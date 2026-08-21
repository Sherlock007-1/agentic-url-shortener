package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

	List<AuditEvent> findByWorkflowRunIdOrderByIdAsc(UUID workflowRunId);
}
