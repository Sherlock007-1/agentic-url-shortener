package com.agenticsdlc.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables of the single-instance orchestration engine.
 *
 * @param corePoolSize    worker threads kept alive for task execution
 * @param maxPoolSize     upper bound of worker threads
 * @param queueCapacity   queued tasks before the caller runs them itself
 * @param schedulerEnabled enables the background poller (tests may drive manually)
 */
@ConfigurationProperties(prefix = "orchestrator")
public record OrchestratorProperties(int corePoolSize, int maxPoolSize, int queueCapacity, Boolean schedulerEnabled) {

	public OrchestratorProperties {
		corePoolSize = corePoolSize <= 0 ? 4 : corePoolSize;
		maxPoolSize = maxPoolSize <= 0 ? Math.max(4, corePoolSize) : maxPoolSize;
		queueCapacity = queueCapacity <= 0 ? 100 : queueCapacity;
		schedulerEnabled = schedulerEnabled == null || schedulerEnabled;
	}
}
