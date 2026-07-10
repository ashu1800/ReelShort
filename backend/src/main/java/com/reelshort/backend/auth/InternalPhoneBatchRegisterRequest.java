package com.reelshort.backend.auth;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InternalPhoneBatchRegisterRequest(
		@NotEmpty @Size(max = 100) List<@NotNull @Valid InternalPhoneRegisterRequest> accounts) {
}
