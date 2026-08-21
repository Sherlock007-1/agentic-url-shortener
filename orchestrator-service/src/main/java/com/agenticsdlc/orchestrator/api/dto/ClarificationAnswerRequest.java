package com.agenticsdlc.orchestrator.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Human answer to a clarification question.
 *
 * @param answer     the clarified information
 * @param answeredBy who answered (free text; no authentication in this assessment)
 * @param replan     when true the answer becomes the new requirement and a new
 *                   graph version is derived from it
 */
@Schema(name = "ClarificationAnswerRequest", description = "Answer a clarification and optionally trigger a replan")
public record ClarificationAnswerRequest(@NotBlank @Size(max = 4000) String answer, @Size(max = 128) String answeredBy,
		boolean replan) {
}
