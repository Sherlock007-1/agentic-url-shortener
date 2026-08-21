package com.agenticsdlc.shortener.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.shortener.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.shortener.url.domain.ClickEvent;
import com.agenticsdlc.shortener.url.domain.ShortUrl;
import com.agenticsdlc.shortener.url.repository.ClickEventRepository;
import com.agenticsdlc.shortener.url.repository.ShortUrlRepository;
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
 * Greenfield increment: click analytics for shortened URLs.
 *
 * <p>Runs against PostgreSQL (Testcontainers) with the real Flyway schema, so the
 * new {@code click_events} migration, the JPA mapping and the redirect path are all
 * exercised together. Every assertion is on persisted state, never on a counter
 * held in memory.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClickAnalyticsIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ShortUrlRepository shortUrlRepository;

	@Autowired
	private ClickEventRepository clickEventRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void cleanDatabase() {
		clickEventRepository.deleteAll();
		shortUrlRepository.deleteAll();
	}

	@Test
	void aSuccessfulRedirectRecordsExactlyOneClick() throws Exception {
		ShortUrl shortUrl = shortUrlRepository
				.save(new ShortUrl("clk10001", "https://example.com/target", Instant.now(), null));

		mockMvc.perform(get("/{shortCode}", "clk10001")).andExpect(status().isFound());

		assertThat(clickEventRepository.countByShortUrlId(shortUrl.getId())).isEqualTo(1);
		assertThat(clickEventRepository.findAll()).singleElement().satisfies(click -> {
			assertThat(click.getShortUrlId()).isEqualTo(shortUrl.getId());
			assertThat(click.getClickedAt()).isNotNull();
			assertThat(click.getReferrer()).isNull();
		});
	}

	@Test
	void multipleRedirectsIncrementTheAnalytics() throws Exception {
		shortUrlRepository.save(new ShortUrl("clk10002", "https://example.com/target", Instant.now(), null));

		for (int i = 0; i < 3; i++) {
			mockMvc.perform(get("/{shortCode}", "clk10002")).andExpect(status().isFound());
		}

		mockMvc.perform(get("/api/urls/{shortCode}/analytics", "clk10002"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.shortCode").value("clk10002"))
				.andExpect(jsonPath("$.totalClicks").value(3))
				.andExpect(jsonPath("$.lastClickedAt").isNotEmpty())
				.andExpect(jsonPath("$.recentClicks.length()").value(3));
	}

	@Test
	void theOptionalReferrerIsRecordedButNoPersonalDataIs() throws Exception {
		shortUrlRepository.save(new ShortUrl("clk10003", "https://example.com/target", Instant.now(), null));

		mockMvc.perform(get("/{shortCode}", "clk10003").header("Referer", "https://news.example/post"))
				.andExpect(status().isFound());

		mockMvc.perform(get("/api/urls/{shortCode}/analytics", "clk10003"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recentClicks[0].referrer").value("https://news.example/post"));

		// The persisted model itself has no place for IP, user agent or location.
		String analytics = mockMvc.perform(get("/api/urls/{shortCode}/analytics", "clk10003"))
				.andReturn().getResponse().getContentAsString();
		assertThat(objectMapper.readTree(analytics).fieldNames()).toIterable()
				.containsExactlyInAnyOrder("shortCode", "totalClicks", "lastClickedAt", "recentClicks");
	}

	@Test
	void anUnknownShortCodeRecordsNothing() throws Exception {
		mockMvc.perform(get("/{shortCode}", "nocode01")).andExpect(status().isNotFound());

		assertThat(clickEventRepository.count()).isZero();
	}

	@Test
	void anExpiredShortUrlRecordsNothing() throws Exception {
		ShortUrl expired = shortUrlRepository.save(new ShortUrl("clk10004", "https://example.com/old",
				Instant.now().minus(2, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS)));

		mockMvc.perform(get("/{shortCode}", "clk10004")).andExpect(status().isGone());

		assertThat(clickEventRepository.countByShortUrlId(expired.getId())).isZero();
	}

	@Test
	void aDisabledShortUrlRecordsNothingAfterItWasDisabled() throws Exception {
		ShortUrl shortUrl = shortUrlRepository
				.save(new ShortUrl("clk10005", "https://example.com/target", Instant.now(), null));

		mockMvc.perform(get("/{shortCode}", "clk10005")).andExpect(status().isFound());
		mockMvc.perform(delete("/api/urls/{shortCode}", "clk10005")).andExpect(status().isNoContent());
		mockMvc.perform(get("/{shortCode}", "clk10005")).andExpect(status().isGone());

		// The click served before disabling is history and stays; the 410 adds nothing.
		assertThat(clickEventRepository.countByShortUrlId(shortUrl.getId())).isEqualTo(1);
	}

	@Test
	void analyticsForAnUnknownShortCodeReturn404() throws Exception {
		mockMvc.perform(get("/api/urls/{shortCode}/analytics", "nocode02"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Short URL not found"));
	}

	@Test
	void analyticsOfANeverClickedShortUrlAreEmptyRatherThanMissing() throws Exception {
		shortUrlRepository.save(new ShortUrl("clk10006", "https://example.com/target", Instant.now(), null));

		mockMvc.perform(get("/api/urls/{shortCode}/analytics", "clk10006"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalClicks").value(0))
				.andExpect(jsonPath("$.lastClickedAt").doesNotExist())
				.andExpect(jsonPath("$.recentClicks.length()").value(0));
	}

	@Test
	void analyticsSurviveARepositoryReload() throws Exception {
		ShortUrl shortUrl = shortUrlRepository
				.save(new ShortUrl("clk10007", "https://example.com/target", Instant.now(), null));

		mockMvc.perform(get("/{shortCode}", "clk10007")).andExpect(status().isFound());
		mockMvc.perform(get("/{shortCode}", "clk10007")).andExpect(status().isFound());

		// Re-read through a fresh repository call: the counts come from the database.
		assertThat(clickEventRepository.countByShortUrlId(shortUrl.getId())).isEqualTo(2);
		assertThat(clickEventRepository.findByShortUrlIdOrderByClickedAtDesc(shortUrl.getId(),
				org.springframework.data.domain.PageRequest.of(0, 10)))
				.hasSize(2)
				.allSatisfy(click -> assertThat(click.getId()).isNotNull());

		mockMvc.perform(get("/api/urls/{shortCode}/analytics", "clk10007"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalClicks").value(2));
	}

	@Test
	void clicksRecordedThroughTheFullCreateThenRedirectFlow() throws Exception {
		String body = mockMvc.perform(post("/api/urls")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"url": "https://example.com/analytics-flow"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String shortCode = objectMapper.readTree(body).get("shortCode").asText();

		mockMvc.perform(get("/{shortCode}", shortCode)).andExpect(status().isFound());

		mockMvc.perform(get("/api/urls/{shortCode}/analytics", shortCode))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalClicks").value(1));
		assertThat(clickEventRepository.findAll()).extracting(ClickEvent::getReferrer).containsExactly((String) null);
	}
}
