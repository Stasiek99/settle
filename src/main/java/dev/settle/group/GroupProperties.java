package dev.settle.group;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("settle.groups")
public record GroupProperties(@Positive int maxMembers) {
}
