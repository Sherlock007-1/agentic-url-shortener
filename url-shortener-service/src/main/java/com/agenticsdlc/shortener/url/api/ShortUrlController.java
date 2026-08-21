package com.agenticsdlc.shortener.url.api;

import com.agenticsdlc.shortener.url.api.dto.CreateShortUrlRequest;
import com.agenticsdlc.shortener.url.api.dto.ShortUrlResponse;
import com.agenticsdlc.shortener.url.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Management API for short URLs.
 */
@RestController
@RequestMapping("/api/urls")
@Tag(name = "Short URLs", description = "Create, inspect and disable shortened URLs")
public class ShortUrlController {

	private final ShortUrlService service;

	public ShortUrlController(ShortUrlService service) {
		this.service = service;
	}

	@PostMapping
	@Operation(summary = "Create a short URL")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Short URL created"),
			@ApiResponse(responseCode = "400", description = "Invalid destination URL or expiration", content = @io.swagger.v3.oas.annotations.media.Content)
	})
	public ResponseEntity<ShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request) {
		ShortUrlResponse response = service.create(request);
		return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
	}

	@GetMapping("/{shortCode}")
	@Operation(summary = "Look up metadata for a short code")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Metadata found"),
			@ApiResponse(responseCode = "404", description = "Unknown short code", content = @io.swagger.v3.oas.annotations.media.Content)
	})
	public ShortUrlResponse get(@PathVariable String shortCode) {
		return service.getMetadata(shortCode);
	}

	@GetMapping("/{shortCode}/analytics")
	@Operation(summary = "Click analytics for a short code",
			description = "Counts successful redirects only. No IP address, user agent or location is stored.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Analytics found"),
			@ApiResponse(responseCode = "404", description = "Unknown short code", content = @io.swagger.v3.oas.annotations.media.Content)
	})
	public com.agenticsdlc.shortener.url.api.dto.ShortUrlAnalyticsResponse analytics(@PathVariable String shortCode) {
		return service.getAnalytics(shortCode);
	}

	@DeleteMapping("/{shortCode}")
	@Operation(summary = "Disable a short URL (soft delete)",
			description = "The row is retained so the code is never reused; the link stops resolving and returns 410.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Short URL disabled"),
			@ApiResponse(responseCode = "404", description = "Unknown short code", content = @io.swagger.v3.oas.annotations.media.Content)
	})
	public ResponseEntity<Void> disable(@PathVariable String shortCode) {
		service.disable(shortCode);
		return ResponseEntity.noContent().build();
	}
}