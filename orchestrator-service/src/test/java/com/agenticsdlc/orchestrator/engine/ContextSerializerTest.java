package com.agenticsdlc.orchestrator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextSerializerTest {

	private final ContextSerializer serializer = new ContextSerializer(new ObjectMapper());

	@Test
	void roundTripsContextSnapshots() {
		Map<String, String> context = new LinkedHashMap<>();
		context.put("requirement-analysis", "scope: analytics");
		context.put("planning", "steps: 1,2,3");

		String json = serializer.write(context);

		assertThat(json).contains("requirement-analysis");
		assertThat(serializer.read(json)).isEqualTo(context);
	}

	@Test
	void treatsNullAndBlankAsEmptyContext() {
		assertThat(serializer.read(null)).isEmpty();
		assertThat(serializer.read("  ")).isEmpty();
	}
}
