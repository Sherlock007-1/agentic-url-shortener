package com.agenticsdlc.shortener.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.shortener.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.shortener.url.domain.ShortUrl;
import com.agenticsdlc.shortener.url.repository.ShortUrlRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end API tests running against PostgreSQL (Testcontainers) with the real
 * Flyway schema, JPA mappings and web layer.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShortUrlApiIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ShortUrlRepository repository;

	@Autowired
	private com.agenticsdlc.shortener.url.repository.ClickEventRepository clickEventRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void cleanDatabase() {
		// Click events reference short URLs, so they have to go first.
		clickEventRepository.deleteAll();
		repository.deleteAll();
	}

	@Test
	void createsShortUrlAndPersistsIt() throws Exception {
		String body = mockMvc.perform(post("/api/urls")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"url": "https://example.com/docs/page"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.originalUrl").value("https://example.com/docs/page"))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.expired").value(false))
				.andExpect(jsonPath("$.shortCode").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		String shortCode = objectMapper.readTree(body).get("shortCode").asText();
		assertThat(repository.findByShortCode(shortCode)).isPresent();
		assertThat(repository.findByShortCode(shortCode).orElseThrow().getCreatedAt()).isNotNull();
	}

	@Test
	void rejectsInvalidDestinationUrl() throws Exception {
		mockMvc.perform(post("/api/urls")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"url": "javascript:alert(1)"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid URL"));

		assertThat(repository.count()).isZero();
	}

	@Test
	void rejectsBlankUrl() throws Exception {
		mockMvc.perform(post("/api/urls")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"url": "  "}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void looksUpMetadataByShortCode() throws Exception {
		Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
		ShortUrl saved = repository.save(
				new ShortUrl("meta123", "https://example.com/meta", Instant.now(), expiresAt));

		mockMvc.perform(get("/api/urls/{shortCode}", saved.getShortCode()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.shortCode").value("meta123"))
				.andExpect(jsonPath("$.originalUrl").value("https://example.com/meta"))
				.andExpect(jsonPath("$.shortUrl").value("http://localhost:8081/meta123"))
				.andExpect(jsonPath("$.expiresAt").isNotEmpty())
				.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void metadataLookupForUnknownCodeReturns404() throws Exception {
		mockMvc.perform(get("/api/urls/{shortCode}", "unknown1"))
				.andExpect(status().isNotFound());
	}

	@Test
	void redirectsActiveShortUrl() throws Exception {
		repository.save(new ShortUrl("live1234", "https://example.com/live", Instant.now(), null));

		mockMvc.perform(get("/{shortCode}", "live1234"))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "https://example.com/live"));
	}

	@Test
	void unknownShortCodeReturns404() throws Exception {
		mockMvc.perform(get("/{shortCode}", "nope1234"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Short URL not found"));
	}

	@Test
	void expiredShortUrlReturns410() throws Exception {
		repository.save(new ShortUrl("expd1234", "https://example.com/old",
				Instant.now().minus(2, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS)));

		mockMvc.perform(get("/{shortCode}", "expd1234"))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("expired")));
	}

	@Test
	void disabledShortUrlReturns410() throws Exception {
		ShortUrl shortUrl = new ShortUrl("dsbl1234", "https://example.com/disabled", Instant.now(), null);
		shortUrl.disable();
		repository.save(shortUrl);

		mockMvc.perform(get("/{shortCode}", "dsbl1234"))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("disabled")));
	}

	@Test
	void deleteSoftDisablesShortUrlAndKeepsTheRow() throws Exception {
		repository.save(new ShortUrl("soft1234", "https://example.com/soft", Instant.now(), null));

		mockMvc.perform(delete("/api/urls/{shortCode}", "soft1234"))
				.andExpect(status().isNoContent());

		ShortUrl reloaded = repository.findByShortCode("soft1234").orElseThrow();
		assertThat(reloaded.isActive()).isFalse();

		mockMvc.perform(get("/{shortCode}", "soft1234")).andExpect(status().isGone());
		mockMvc.perform(get("/api/urls/{shortCode}", "soft1234"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void deleteUnknownShortCodeReturns404() throws Exception {
		mockMvc.perform(delete("/api/urls/{shortCode}", "gone9999"))
				.andExpect(status().isNotFound());
	}

	@Test
	void fullLifecycleThroughTheApi() throws Exception {
		String body = mockMvc.perform(post("/api/urls")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"url": "https://example.com/lifecycle"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		JsonNode created = objectMapper.readTree(body);
		String shortCode = created.get("shortCode").asText();

		mockMvc.perform(get("/{shortCode}", shortCode))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "https://example.com/lifecycle"));

		mockMvc.perform(delete("/api/urls/{shortCode}", shortCode)).andExpect(status().isNoContent());
		mockMvc.perform(get("/{shortCode}", shortCode)).andExpect(status().isGone());
	}

	@Test
	void openApiDocumentExposesTheShortUrlApi() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/urls']").exists())
				.andExpect(jsonPath("$.paths['/api/urls/{shortCode}']").exists())
				.andExpect(jsonPath("$.paths['/api/urls/{shortCode}/analytics']").exists());
	}
}
