package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.TaskDependency;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {

	List<TaskDependency> findByTaskIdIn(Collection<UUID> taskIds);

	List<TaskDependency> findByTaskId(UUID taskId);
}
