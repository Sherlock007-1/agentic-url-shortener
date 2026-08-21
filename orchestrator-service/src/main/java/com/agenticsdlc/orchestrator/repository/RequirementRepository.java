package com.agenticsdlc.orchestrator.repository;

import com.agenticsdlc.orchestrator.domain.Requirement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementRepository extends JpaRepository<Requirement, UUID> {
}
