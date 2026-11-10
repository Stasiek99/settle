package dev.settle.group;

import dev.settle.common.NoControlCharacters;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(@NotBlank @Size(max = 100) @NoControlCharacters String name) {
}
