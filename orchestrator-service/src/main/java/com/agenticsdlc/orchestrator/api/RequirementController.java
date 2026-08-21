package com.agenticsdlc.orchestrator.api;

import com.agenticsdlc.orchestrator.api.dto.CreateRequirementRequest;
import com.agenticsdlc.orchestrator.api.dto.WorkflowResponse;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Entry point of the orchestration: submitting a requirement. */
@RestController
@RequestMapping("/api/requirements")
@Tag(name = "Requirements", description = "Submit requirements to the orchestrator")
public class RequirementController {

	private final WorkflowService workflowService;

	public RequirementController(WorkflowService workflowService) {
		this.workflowService = workflowService;
	}

	@PostMapping
	@Operation(summary = "Create a requirement, its workflow run and graph version 1")
	public ResponseEntity<WorkflowResponse> create(@Valid @RequestBody CreateRequirementRequest request) {
		WorkflowRun run = workflowService.createWorkflow(request.text());
		WorkflowResponse response = WorkflowResponse.from(run, request.text());
		return ResponseEntity.created(URI.create("/api/workflows/" + run.getId())).body(response);
	}
}
