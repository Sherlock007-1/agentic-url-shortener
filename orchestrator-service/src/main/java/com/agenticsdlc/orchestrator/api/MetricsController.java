package com.agenticsdlc.orchestrator.api;

import com.agenticsdlc.orchestrator.metrics.MetricsResponse;
import com.agenticsdlc.orchestrator.metrics.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Metrics derived from persisted orchestration data. */
@RestController
@RequestMapping("/api/metrics")
@Tag(name = "Metrics", description = "Success rate, retries, rollbacks, MTTR and latency from persisted records")
public class MetricsController {

	private final MetricsService metricsService;

	public MetricsController(MetricsService metricsService) {
		this.metricsService = metricsService;
	}

	@GetMapping
	@Operation(summary = "Orchestration metrics; metrics without samples are null instead of fabricated")
	public MetricsResponse metrics() {
		return metricsService.metrics();
	}
}
