package dev.settle.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProdProfileConfigTest {

    private PropertySource<?> prod;

    @BeforeEach
    void loadProdProfile() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.yaml"));
        assertThat(sources).hasSize(1);
        prod = sources.get(0);
    }

    @Test
    void apiDocsAndSwaggerUiAreSwitchedOff() {
        assertThat(prod.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
    }

    @Test
    void logsAreWrittenAsEcsJson() {
        assertThat(prod.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    }
}
