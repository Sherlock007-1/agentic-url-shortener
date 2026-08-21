package com.agenticsdlc.orchestrator.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request a new graph version.
 *
 * @param changedRequirement clarified/changed requirement text; null keeps the current one
 * @param reason             why replanning is necessary (persisted lineage, mandatory)
 */
@Schema(name = "ReplanRequest", description = "Create the next graph version from a changed requirement")
public record ReplanRequest(@Size(max = 4000) String changedRequirement, @NotBlank @Size(max = 2000) String reason) {
}
