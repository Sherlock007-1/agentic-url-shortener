package com.agenticsdlc.shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the URL shortener service.
 *
 * <p>Shortening, redirect, expiration and analytics behaviour is implemented
 * incrementally; this class only bootstraps the application.
 */
@SpringBootApplication
public class UrlShortenerApplication {

	public static void main(String[] args) {
		SpringApplication.run(UrlShortenerApplication.class, args);
	}
}
