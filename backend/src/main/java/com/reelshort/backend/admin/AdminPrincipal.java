package com.reelshort.backend.admin;

import java.util.Set;
import java.util.UUID;

public record AdminPrincipal(UUID adminUserId, String username, Set<String> permissions) {

	public AdminPrincipal {
		permissions = Set.copyOf(permissions);
	}

	public AdminPrincipal(String username) {
		this(null, username, Set.of());
	}
}
