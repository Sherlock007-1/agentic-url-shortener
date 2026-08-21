package com.agenticsdlc.orchestrator.engine;

import com.agenticsdlc.orchestrator.domain.AuditEvent;
import com.agenticsdlc.orchestrator.domain.Decision;
import com.agenticsdlc.orchestrator.domain.TaskDependency;
import com.agenticsdlc.orchestrator.domain.WorkflowGraphVersion;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.repository.AuditEventRepository;
import com.agenticsdlc.orchestrator.repository.DecisionRepository;
import com.agenticsdlc.orchestrator.repository.TaskDependencyRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowGraphVersionRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side of the orchestration core: graph, tasks, decisions and audit history
 * are always served from the database, never from engine memory.
 */
@Service
public class WorkflowQueryService {

	private final WorkflowGraphVersionRepository graphVersionRepository;
	private final WorkflowTaskRepository taskRepository;
	private final TaskDependencyRepository dependencyRepository;
	private final DecisionRepository decisionRepository;
	private final AuditEventRepository auditEventRepository;

	public WorkflowQueryService(WorkflowGraphVersionRepository graphVersionRepository,
			WorkflowTaskRepository taskRepository, TaskDependencyRepository dependencyRepository,
			DecisionRepository decisionRepository, AuditEventRepository auditEventRepository) {
		this.graphVersionRepository = graphVersionRepository;
		this.taskRepository = taskRepository;
		this.dependencyRepository = dependencyRepository;
		this.decisionRepository = decisionRepository;
		this.auditEventRepository = auditEventRepository;
	}

	@Transactional(readOnly = true)
	public List<WorkflowTask> tasks(UUID workflowRunId) {
		return taskRepository.findByWorkflowRunIdOrderBySequenceNoAsc(workflowRunId);
	}

	@Transactional(readOnly = true)
	public WorkflowGraphVersion graphVersion(UUID workflowRunId, int version) {
		return graphVersionRepository.findByWorkflowRunIdAndVersion(workflowRunId, version)
				.orElseThrow(() -> new WorkflowNotFoundException(
						"Graph version " + version + " not found for workflow " + workflowRunId));
	}

	/** Dependency task keys per task key, resolved from the persisted edges. */
	@Transactional(readOnly = true)
	public Map<String, List<String>> dependencyKeys(List<WorkflowTask> tasks) {
		Map<UUID, String> keysById = new HashMap<>();
		tasks.forEach(task -> keysById.put(task.getId(), task.getTaskKey()));
		Map<String, List<String>> result = new HashMap<>();
		tasks.forEach(task -> result.put(task.getTaskKey(), new ArrayList<>()));
		if (tasks.isEmpty()) {
			return result;
		}
		for (TaskDependency edge : dependencyRepository.findByTaskIdIn(keysById.keySet())) {
			String taskKey = keysById.get(edge.getTaskId());
			String dependsOnKey = keysById.get(edge.getDependsOnTaskId());
			if (taskKey != null && dependsOnKey != null) {
				result.get(taskKey).add(dependsOnKey);
			}
		}
		result.values().forEach(java.util.Collections::sort);
		return result;
	}

	@Transactional(readOnly = true)
	public List<Decision> decisions(UUID workflowRunId) {
		return decisionRepository.findByWorkflowRunIdOrderByCreatedAtAsc(workflowRunId);
	}

	@Transactional(readOnly = true)
	public List<AuditEvent> auditTrail(UUID workflowRunId) {
		return auditEventRepository.findByWorkflowRunIdOrderByIdAsc(workflowRunId);
	}
}
