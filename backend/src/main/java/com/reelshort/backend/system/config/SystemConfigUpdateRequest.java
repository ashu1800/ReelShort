package com.reelshort.backend.system.config;

import jakarta.validation.constraints.NotBlank;

public record SystemConfigUpdateRequest(@NotBlank String value) {
}
