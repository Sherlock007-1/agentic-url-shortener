package com.agenticsdlc.orchestrator.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Serialises the cross-stage context snapshot stored on each task.
 *
 * <p>Plain JSON text keeps the schema simple and the data inspectable in SQL.
 */
@Component
public class ContextSerializer {

	private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper;

	public ContextSerializer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public String write(Map<String, String> context) {
		try {
			return objectMapper.writeValueAsString(context);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Unable to serialise task context", ex);
		}
	}

	public Map<String, String> read(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(json, MAP_TYPE);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Unable to deserialise task context", ex);
		}
	}
}
