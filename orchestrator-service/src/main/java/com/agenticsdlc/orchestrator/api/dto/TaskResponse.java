package com.agenticsdlc.orchestrator.api.dto;

import com.agenticsdlc.orchestrator.domain.AgentType;
import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "TaskResponse", description = "A task of the workflow graph")
public record TaskResponse(UUID taskId, String taskKey, String name, AgentType agentType, TaskStatus status,
		int sequenceNo, List<String> dependsOn, String inputContext, String outputContext, String errorMessage,
		Instant startedAt, Instant completedAt,
		@Schema(description = "Bounded agent/task attempts executed; not the engine's optimistic-lock retries")
		int attemptCount) {

	public static TaskResponse from(WorkflowTask task, List<String> dependsOn) {
		return new TaskResponse(task.getId(), task.getTaskKey(), task.getName(), task.getAgentType(), task.getStatus(),
				task.getSequenceNo(), dependsOn, task.getInputContext(), task.getOutputContext(),
				task.getErrorMessage(), task.getStartedAt(), task.getCompletedAt(), task.getAttemptCount());
	}
}