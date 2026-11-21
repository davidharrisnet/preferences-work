package org.bighouse.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	/**
	 * Swagger UI's "Try it out" sends requests to this server URL instead of the host that served
	 * the page. Override with OPENAPI_SERVER_URL.
	 */
	@Bean
	OpenAPI preferencesOpenApi(@Value("${openapi.server-url}") String serverUrl) {
		return new OpenAPI()
				.info(new Info()
						.title("Preferences API")
						.version("0.0.1")
						.description("REST API for users, stored in SQLite."))
				.addServersItem(new Server().url(serverUrl));
	}

}
