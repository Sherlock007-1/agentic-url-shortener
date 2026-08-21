package com.agenticsdlc.orchestrator.engine;

import com.agenticsdlc.orchestrator.agent.Agent;
import com.agenticsdlc.orchestrator.agent.AgentContext;
import com.agenticsdlc.orchestrator.agent.AgentRegistry;
import com.agenticsdlc.orchestrator.agent.AgentResult;
import com.agenticsdlc.orchestrator.engine.WorkflowTransitionService.ClaimedTask;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs a single claimed task: invokes its agent outside any transaction and then
 * persists the outcome through {@link WorkflowTransitionService}.
 */
@Component
public class TaskRunner {

	private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);

	private final AgentRegistry agentRegistry;
	private final WorkflowTransitionService transitionService;

	public TaskRunner(AgentRegistry agentRegistry, WorkflowTransitionService transitionService) {
		this.agentRegistry = agentRegistry;
		this.transitionService = transitionService;
	}

	public void run(UUID workflowRunId, ClaimedTask claimed) {
		AgentContext context = new AgentContext(workflowRunId, claimed.taskId(), claimed.taskKey(),
				claimed.requirement(), claimed.upstreamOutputs());
		try {
			Agent agent = agentRegistry.require(claimed.agentType());
			AgentResult result = agent.execute(context);
			transitionService.completeTask(claimed.taskId(), result.output(), result.summary(), result.decisions(),
					claimed.upstreamOutputs());
		}
		catch (RuntimeException ex) {
			log.warn("Task {} ({}) failed: {}", claimed.taskKey(), claimed.taskId(), ex.toString());
			String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			transitionService.failTask(claimed.taskId(), message, claimed.upstreamOutputs());
		}
	}
}
