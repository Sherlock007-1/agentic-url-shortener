package com.agenticsdlc.orchestrator.engine;

import com.agenticsdlc.orchestrator.domain.TaskDependency;
import com.agenticsdlc.orchestrator.domain.WorkflowGraphVersion;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate;
import com.agenticsdlc.orchestrator.graph.TaskDefinition;
import com.agenticsdlc.orchestrator.repository.TaskDependencyRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowGraphVersionRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materialises a graph template into persisted tasks and dependency edges.
 *
 * <p>The engine never consults the template at runtime: scheduling decisions are
 * made purely from the rows written here.
 */
@Service
public class WorkflowGraphService {

	private final WorkflowGraphVersionRepository graphVersionRepository;
	private final WorkflowTaskRepository taskRepository;
	private final TaskDependencyRepository dependencyRepository;
	private final Clock clock;

	public WorkflowGraphService(WorkflowGraphVersionRepository graphVersionRepository,
			WorkflowTaskRepository taskRepository, TaskDependencyRepository dependencyRepository, Clock clock) {
		this.graphVersionRepository = graphVersionRepository;
		this.taskRepository = taskRepository;
		this.dependencyRepository = dependencyRepository;
		this.clock = clock;
	}

	/**
	 * Creates graph version 1 for a workflow from the baseline SDLC template.
	 *
	 * @return the persisted graph version
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public WorkflowGraphVersion createInitialGraph(UUID workflowRunId) {
		Instant now = clock.instant();
		WorkflowGraphVersion graphVersion = graphVersionRepository.save(new WorkflowGraphVersion(workflowRunId,
				SdlcWorkflowGraphTemplate.VERSION, SdlcWorkflowGraphTemplate.DESCRIPTION, now));

		List<TaskDefinition> definitions = SdlcWorkflowGraphTemplate.tasks();
		Map<String, UUID> taskIdsByKey = new HashMap<>();
		int sequence = 0;
		for (TaskDefinition definition : definitions) {
			WorkflowTask task = taskRepository.save(new WorkflowTask(workflowRunId, graphVersion.getId(),
					definition.key(), definition.name(), definition.agentType(), sequence++, now));
			taskIdsByKey.put(definition.key(), task.getId());
		}

		List<TaskDependency> edges = new ArrayList<>();
		for (TaskDefinition definition : definitions) {
			UUID taskId = taskIdsByKey.get(definition.key());
			for (String dependency : definition.dependsOn()) {
				UUID dependsOnId = taskIdsByKey.get(dependency);
				if (dependsOnId == null) {
					throw new IllegalStateException(
							"Graph template references unknown task '" + dependency + "'");
				}
				edges.add(new TaskDependency(taskId, dependsOnId));
			}
		}
		dependencyRepository.saveAll(edges);
		return graphVersion;
	}
}
