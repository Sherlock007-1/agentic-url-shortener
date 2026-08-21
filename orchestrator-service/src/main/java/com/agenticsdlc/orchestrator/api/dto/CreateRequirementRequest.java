package com.agenticsdlc.orchestrator.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateRequirementRequest", description = "Natural language requirement to orchestrate")
public record CreateRequirementRequest(

		@Schema(example = "Add click analytics for shortened URLs.")
		@NotBlank(message = "text must not be blank")
		@Size(max = 4000, message = "text must not exceed 4000 characters")
		String text) {
}
