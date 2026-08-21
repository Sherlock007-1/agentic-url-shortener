package com.agenticsdlc.shortener.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.shortener.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.shortener.url.domain.ShortUrl;
import com.agenticsdlc.shortener.url.repository.ClickEventRepository;
import com.agenticsdlc.shortener.url.repository.ShortUrlRepository;
import com.agenticsdlc.shortener.url.service.ShortCodeGenerator;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Brownfield increment proven against the real database.
 *
 * <p>The unit tests script the repository; this test scripts only the generator and
 * lets PostgreSQL raise the actual {@code uk_short_urls_short_code} violation. That
 * keeps the unique constraint as the real safety boundary while showing that the
 * bounded retry is what turns a collision into a successful create.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShortCodeCollisionRetryIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String CREATE_BODY = """
			{"url": "https://example.com/collision"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ShortUrlRepository shortUrlRepository;

	@Autowired
	private ClickEventRepository clickEventRepository;

	@MockBean
	private ShortCodeGenerator codeGenerator;

	@BeforeEach
	void cleanDatabase() {
		clickEventRepository.deleteAll();
		shortUrlRepository.deleteAll();
	}

	@Test
	void aCollisionWithAnExistingRowIsResolvedByRegenerating() throws Exception {
		shortUrlRepository.save(new ShortUrl("taken001", "https://example.com/existing", Instant.now(), null));
		when(codeGenerator.generate()).thenReturn("taken001", "fresh001");

		mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shortCode").value("fresh001"));

		assertThat(shortUrlRepository.findByShortCode("fresh001")).isPresent();
		assertThat(shortUrlRepository.count()).isEqualTo(2);
	}

	@Test
	void twoCollisionsStillSucceedOnTheThirdAttempt() throws Exception {
		shortUrlRepository.save(new ShortUrl("taken001", "https://example.com/existing-1", Instant.now(), null));
		shortUrlRepository.save(new ShortUrl("taken002", "https://example.com/existing-2", Instant.now(), null));
		when(codeGenerator.generate()).thenReturn("taken001", "taken002", "fresh002");

		mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shortCode").value("fresh002"));

		assertThat(shortUrlRepository.count()).isEqualTo(3);
	}

	@Test
	void threeCollisionsFailWithAConflictInsteadOfLoopingForever() throws Exception {
		shortUrlRepository.save(new ShortUrl("taken001", "https://example.com/existing-1", Instant.now(), null));
		shortUrlRepository.save(new ShortUrl("taken002", "https://example.com/existing-2", Instant.now(), null));
		shortUrlRepository.save(new ShortUrl("taken003", "https://example.com/existing-3", Instant.now(), null));
		when(codeGenerator.generate()).thenReturn("taken001", "taken002", "taken003", "fresh003");

		mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Could not generate a unique short code"))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("3 attempts")));

		// The fourth code was never reached: no extra row was written.
		assertThat(shortUrlRepository.count()).isEqualTo(3);
		assertThat(shortUrlRepository.findByShortCode("fresh003")).isEmpty();
	}

	@Test
	void aFreeCodeOnTheFirstAttemptBehavesExactlyLikeTheBaseline() throws Exception {
		when(codeGenerator.generate()).thenReturn("fresh004");

		mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shortCode").value("fresh004"))
				.andExpect(jsonPath("$.shortUrl").value("http://localhost:8081/fresh004"))
				.andExpect(jsonPath("$.active").value(true));

		assertThat(shortUrlRepository.count()).isEqualTo(1);
	}
}
