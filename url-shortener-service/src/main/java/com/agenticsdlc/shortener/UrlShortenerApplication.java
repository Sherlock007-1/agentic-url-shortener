package com.agenticsdlc.shortener;

import com.agenticsdlc.shortener.url.config.ShortenerProperties;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the URL shortener service.
 *
 * <p>Shortening, redirect, expiration and analytics behaviour is implemented
 * incrementally; this class only bootstraps the application.
 */
@SpringBootApplication
@EnableConfigurationProperties(ShortenerProperties.class)
@OpenAPIDefinition(info = @Info(title = "URL Shortener API", version = "v1",
		description = "Create, resolve, inspect and disable shortened URLs"))
public class UrlShortenerApplication {

	public static void main(String[] args) {
		SpringApplication.run(UrlShortenerApplication.class, args);
	}
}