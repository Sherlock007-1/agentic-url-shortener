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

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
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
}
