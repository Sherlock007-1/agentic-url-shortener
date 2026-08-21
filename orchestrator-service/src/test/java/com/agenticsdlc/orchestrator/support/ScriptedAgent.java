package com.agenticsdlc.orchestrator.support;

import com.agenticsdlc.orchestrator.agent.Agent;
import com.agenticsdlc.orchestrator.agent.AgentContext;
import com.agenticsdlc.orchestrator.agent.AgentResult;
import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic agent whose behaviour is scripted per workflow and per attempt.
 *
 * <p>The script is keyed by workflow id, so test methods that share a Spring
 * context (and therefore this singleton bean) can never consume each other's
 * steps. Attempt <em>n</em> of a workflow executes step <em>n</em>; once a script
 * is exhausted the agent succeeds.
 */
public class ScriptedAgent implements Agent {

	/** One scripted attempt: returns a result or throws. */
	@FunctionalInterface
	public interface Step {
		AgentResult apply(AgentContext context);
	}

	public static final Step SUCCESS = context -> AgentResult.of("SCRIPTED-OK:" + context.taskKey(),
			"scripted success");

	private final AgentType type;
	private final Map<UUID, List<Step>> scripts = new ConcurrentHashMap<>();
	private final Map<UUID, AtomicInteger> invocations = new ConcurrentHashMap<>();

	public ScriptedAgent(AgentType type) {
		this.type = type;
	}

	/** Scripts the attempts of one workflow; must be called before it is started. */
	public void scriptFor(UUID workflowRunId, Step... script) {
		scripts.put(workflowRunId, List.of(script));
		invocations.put(workflowRunId, new AtomicInteger());
	}

	public int invocationsFor(UUID workflowRunId) {
		return invocations.getOrDefault(workflowRunId, new AtomicInteger()).get();
	}

	@Override
	public AgentType type() {
		return type;
	}

	@Override
	public AgentResult execute(AgentContext context) {
		int attempt = invocations.computeIfAbsent(context.workflowRunId(), key -> new AtomicInteger())
				.getAndIncrement();
		List<Step> script = scripts.getOrDefault(context.workflowRunId(), List.of());
		Step step = attempt < script.size() ? script.get(attempt) : SUCCESS;
		return step.apply(context);
	}
}