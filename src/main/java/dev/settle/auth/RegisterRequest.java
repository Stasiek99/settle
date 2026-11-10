package dev.settle.auth;

import dev.settle.common.NoControlCharacters;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) @NoControlCharacters String displayName,
        @NotBlank @Size(min = 8, max = 72) @NoControlCharacters String password
) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", displayName=" + displayName + ", password=***]";
    }
}
