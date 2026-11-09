package dev.settle.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(min = 8, max = 72) String password
) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", displayName=" + displayName + ", password=***]";
    }
}
