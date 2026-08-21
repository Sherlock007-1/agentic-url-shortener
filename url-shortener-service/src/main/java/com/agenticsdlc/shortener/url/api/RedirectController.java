package com.agenticsdlc.shortener.url.api;

import com.agenticsdlc.shortener.url.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public redirect endpoint.
 *
 * <p>The path variable is constrained to the Base62 short-code shape so that this
 * catch-all mapping cannot swallow other application paths (Swagger UI, actuator, API).
 */
@RestController
@Tag(name = "Redirect", description = "Public short link resolution")
public class RedirectController {

	private final ShortUrlService service;

	public RedirectController(ShortUrlService service) {
		this.service = service;
	}

	@GetMapping("/{shortCode:[A-Za-z0-9]{4,16}}")
	@Operation(summary = "Redirect to the destination URL")
	@ApiResponses({
			@ApiResponse(responseCode = "302", description = "Redirect to the destination URL", content = @io.swagger.v3.oas.annotations.media.Content),
			@ApiResponse(responseCode = "404", description = "Unknown short code", content = @io.swagger.v3.oas.annotations.media.Content),
			@ApiResponse(responseCode = "410", description = "Short URL expired or disabled", content = @io.swagger.v3.oas.annotations.media.Content)
	})
	public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
		String destination = service.resolveDestination(shortCode);
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(destination))
				.cacheControl(org.springframework.http.CacheControl.noStore())
				.build();
	}
}
