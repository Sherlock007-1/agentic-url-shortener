package com.agenticsdlc.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The natural-language requirement that a workflow run is driving. */
@Entity
@Table(name = "requirements")
public class Requirement {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "text", nullable = false)
	private String text;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Requirement() {
		// for JPA
	}

	public Requirement(String text, Instant createdAt) {
		this.text = text;
		this.createdAt = createdAt;
	}

	/**
	 * Replaces the requirement text after a clarification/replan. The previous text
	 * is not lost: it is persisted on the {@code workflow_replans} lineage row.
	 */
	public void updateText(String text) {
		this.text = text;
	}

	public UUID getId() {
		return id;
	}

	public String getText() {
		return text;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}