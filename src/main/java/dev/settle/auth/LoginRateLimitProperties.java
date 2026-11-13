package dev.settle.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("settle.security.login-rate-limit")
public record LoginRateLimitProperties(
        @NotNull Duration window,
        @Positive int maxPerClientAndAccount,
        @Positive int maxPerClient,
        @Positive int maxPerAccount,
        @Positive long maxTrackedKeys
) {
}
