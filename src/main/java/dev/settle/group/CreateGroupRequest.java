package dev.settle.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param createdBy temporary: until authentication exists (phase 4) the caller says who they
 *                  are. Once there is a logged-in user this field disappears.
 */
public record CreateGroupRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter currency code") String currency,
        @NotNull UUID createdBy
) {
}
