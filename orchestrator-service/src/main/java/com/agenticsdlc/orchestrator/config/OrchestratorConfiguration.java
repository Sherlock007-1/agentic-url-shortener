package com.agenticsdlc.orchestrator.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Orchestration infrastructure: a bounded worker pool for parallel task execution
 * and the poller that drives workflows forward.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OrchestratorProperties.class)
public class OrchestratorConfiguration {

	public static final String TASK_EXECUTOR = "orchestratorTaskExecutor";

	public static final String AGENT_TIMEOUT_EXECUTOR = "agentTimeoutExecutor";

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

	/** Convenience bean so governance components inject only what they need. */
	@Bean
	public GovernanceProperties governanceProperties(OrchestratorProperties properties) {
		return properties.governance();
	}

	/**
	 * Bounded pool: parallelism is intentionally capped so a large graph cannot
	 * exhaust the JVM. {@code CallerRunsPolicy} keeps work moving if the queue fills.
	 */
	@Bean(name = TASK_EXECUTOR)
	public TaskExecutor orchestratorTaskExecutor(OrchestratorProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.corePoolSize());
		executor.setMaxPoolSize(properties.maxPoolSize());
		executor.setQueueCapacity(properties.queueCapacity());
		executor.setThreadNamePrefix("orchestrator-task-");
		executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return executor;
	}

	/**
	 * Separate pool used only to bound a single agent attempt with a timeout.
	 *
	 * <p>Kept apart from {@link #TASK_EXECUTOR} on purpose: a hung agent must not be
	 * able to consume the workers that drive the rest of the graph. Threads are
	 * daemons so a stuck agent cannot keep the JVM alive.
	 */
	@Bean(name = AGENT_TIMEOUT_EXECUTOR, destroyMethod = "shutdownNow")
	public java.util.concurrent.ExecutorService agentTimeoutExecutor() {
		return java.util.concurrent.Executors.newCachedThreadPool(runnable -> {
			Thread thread = new Thread(runnable, "agent-attempt-" + java.util.UUID.randomUUID());
			thread.setDaemon(true);
			return thread;
		});
	}
}