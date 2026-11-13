package dev.settle.common;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("settle.web")
public record WebProperties(
        @NotNull @Pattern(regexp = "^(/[A-Za-z0-9._~-]+)*$", message = "must be empty or a path like /api") String publicPath
) {
}
