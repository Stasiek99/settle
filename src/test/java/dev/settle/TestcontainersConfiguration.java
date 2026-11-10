package dev.settle;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcConfigurer;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

	/**
	 * The API protects cookie-authenticated requests against CSRF. Most tests are about something else,
	 * so every MockMvc request carries a valid token; tests of the CSRF protection itself build their own
	 * MockMvc without this configurer.
	 */
	@Bean
	MockMvcBuilderCustomizer csrfTokenOnEveryRequest() {
		return builder -> builder.apply(new MockMvcConfigurer() {
			@Override
			public RequestPostProcessor beforeMockMvcCreated(ConfigurableMockMvcBuilder<?> builder,
															 WebApplicationContext context) {
				return SecurityMockMvcRequestPostProcessors.csrf();
			}
		});
	}

}
