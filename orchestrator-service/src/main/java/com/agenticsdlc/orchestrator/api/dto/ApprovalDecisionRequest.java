package com.agenticsdlc.orchestrator.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Human decision on an approval gate.
 *
 * @param reviewer who decided (free text; no authentication in this assessment)
 * @param comment  why the decision was taken, persisted for the audit trail
 */
@Schema(name = "ApprovalDecisionRequest", description = "Reviewer and rationale of an approval decision")
public record ApprovalDecisionRequest(@Size(max = 128) String reviewer, @Size(max = 2000) String comment) {
}
