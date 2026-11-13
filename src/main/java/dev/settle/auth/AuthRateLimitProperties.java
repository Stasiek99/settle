package dev.settle.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("settle.security.rate-limit")
public record AuthRateLimitProperties(
        @Valid @NotNull Spec register,
        @Valid @NotNull Spec refresh,
        @Positive long maxTrackedKeys
) {

    public record Spec(@NotNull Duration window, @Positive int maxPerClient) {
    }
}
