package dev.settle.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("settle.security.session")
public record SessionProperties(
        @NotNull Duration refreshExpiration,
        @NotNull Duration refreshReuseGrace,
        @NotBlank String apiPath,
        @NotBlank String authPath,
        boolean secureCookies
) {
}
