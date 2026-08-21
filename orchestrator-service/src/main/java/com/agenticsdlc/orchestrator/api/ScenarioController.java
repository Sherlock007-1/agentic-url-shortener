package com.agenticsdlc.orchestrator.api;

import com.agenticsdlc.orchestrator.api.dto.WorkflowResponse;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import com.agenticsdlc.orchestrator.scenario.Scenario;
import com.agenticsdlc.orchestrator.scenario.ScenarioCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reproducible entry points for the three assessment scenarios.
 *
 * <p>Convenience only: starting a scenario is exactly equivalent to posting its
 * requirement text to {@code /api/requirements} and then starting the workflow. The
 * endpoint exists so a reviewer cannot accidentally demo a different requirement
 * than the one the tests and the {@code scenarios/*.json} fixtures describe.
 */
@RestController
@RequestMapping("/api/scenarios")
@Tag(name = "Scenarios", description = "Start the greenfield, brownfield and ambiguous scenarios reproducibly")
public class ScenarioController {

	private final WorkflowService workflowService;

	public ScenarioController(WorkflowService workflowService) {
		this.workflowService = workflowService;
	}

	@GetMapping
	@Operation(summary = "The three assessment scenarios and where their evidence lives")
	public List<Scenario> scenarios() {
		return ScenarioCatalog.all();
	}

	@GetMapping("/{key}")
	@Operation(summary = "One scenario definition (404 for an unknown key)")
	public Scenario scenario(@PathVariable String key) {
		return ScenarioCatalog.require(key);
	}

	@PostMapping("/{key}/start")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create and start a workflow for this scenario's requirement",
			description = "Equivalent to POST /api/requirements followed by POST /api/workflows/{id}/start. "
					+ "Governance still applies: the run parks at the approval gates, and the ambiguous scenario "
					+ "parks in AWAITING_CLARIFICATION.")
	public WorkflowResponse start(@PathVariable String key) {
		Scenario scenario = ScenarioCatalog.require(key);
		WorkflowRun created = workflowService.createWorkflow(scenario.requirement());
		WorkflowRun started = workflowService.start(created.getId());
		return WorkflowResponse.from(started, workflowService.requirementText(started.getRequirementId()));
	}
}
