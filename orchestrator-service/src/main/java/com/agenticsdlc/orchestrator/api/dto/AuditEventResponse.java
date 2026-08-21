package com.agenticsdlc.orchestrator.api.dto;

import com.agenticsdlc.orchestrator.domain.AuditEvent;
import com.agenticsdlc.orchestrator.domain.AuditEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "AuditEventResponse", description = "One entry of the workflow audit trail")
public record AuditEventResponse(long id, UUID taskId, AuditEventType eventType, String message, String details,
		Instant createdAt) {

	public static AuditEventResponse from(AuditEvent event) {
		return new AuditEventResponse(event.getId(), event.getTaskId(), event.getEventType(), event.getMessage(),
				event.getDetails(), event.getCreatedAt());
	}
}
