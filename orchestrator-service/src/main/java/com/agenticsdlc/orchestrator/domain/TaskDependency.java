package com.agenticsdlc.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A directed edge of the graph: {@code taskId} may only run once
 * {@code dependsOnTaskId} has completed.
 */
@Entity
@Table(name = "task_dependencies")
public class TaskDependency {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "task_id", nullable = false, updatable = false)
	private UUID taskId;

	@Column(name = "depends_on_task_id", nullable = false, updatable = false)
	private UUID dependsOnTaskId;

	protected TaskDependency() {
		// for JPA
	}

	public TaskDependency(UUID taskId, UUID dependsOnTaskId) {
		this.taskId = taskId;
		this.dependsOnTaskId = dependsOnTaskId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTaskId() {
		return taskId;
	}

	public UUID getDependsOnTaskId() {
		return dependsOnTaskId;
	}
}
