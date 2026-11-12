package dev.settle.cache;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("settle.cache")
public record CacheProperties(
        @Valid @NotNull Spec memberships,
        @Valid @NotNull Spec balances,
        @Valid @NotNull Spec suggestions
) {

    public record Spec(@NotNull Duration ttl, @Positive long maxSize) {
    }
}
