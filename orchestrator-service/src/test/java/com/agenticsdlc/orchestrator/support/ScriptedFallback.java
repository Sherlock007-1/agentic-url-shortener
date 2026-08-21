package com.agenticsdlc.orchestrator.support;

import com.agenticsdlc.orchestrator.agent.AgentContext;
import com.agenticsdlc.orchestrator.agent.AgentFallback;
import com.agenticsdlc.orchestrator.agent.AgentResult;
import com.agenticsdlc.orchestrator.domain.AgentType;
import com.agenticsdlc.orchestrator.support.ScriptedAgent.Step;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Approved fallback whose behaviour a test scripts per workflow. */
public class ScriptedFallback implements AgentFallback {

	private static final Step DEFAULT = context -> AgentResult.of("SCRIPTED-FALLBACK:" + context.taskKey(),
			"scripted fallback result");

	private final AgentType primaryType;
	private final Map<UUID, Step> steps = new ConcurrentHashMap<>();
	private final Map<UUID, AtomicInteger> invocations = new ConcurrentHashMap<>();

	public ScriptedFallback(AgentType primaryType) {
		this.primaryType = primaryType;
	}

	public void scriptFor(UUID workflowRunId, Step step) {
		steps.put(workflowRunId, step);
		invocations.put(workflowRunId, new AtomicInteger());
	}

	public int invocationsFor(UUID workflowRunId) {
		return invocations.getOrDefault(workflowRunId, new AtomicInteger()).get();
	}

	@Override
	public AgentType primaryType() {
		return primaryType;
	}

	@Override
	public String description() {
		return "scripted approved fallback for " + primaryType;
	}

	@Override
	public AgentResult execute(AgentContext context) {
		invocations.computeIfAbsent(context.workflowRunId(), key -> new AtomicInteger()).incrementAndGet();
		return steps.getOrDefault(context.workflowRunId(), DEFAULT).apply(context);
	}
}