package org.bighouse.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets browsers on other origins call the /users API directly.
 *
 * Off unless app.cors.allowed-origins is set (by the "direct" or "localhost" profile), so the
 * default davidharris.net configuration, where the frontend uses a same-origin proxy, is unaffected.
 */
@Configuration
@ConditionalOnProperty("app.cors.allowed-origins")
public class CorsConfig implements WebMvcConfigurer {

	/** Comma-separated exact origins (scheme://host:port), e.g. https://davidharris.net:8445. */
	private final String[] allowedOrigins;

	public CorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/users/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST")
				.allowedHeaders("Content-Type")
				.exposedHeaders("Location");
	}

}
