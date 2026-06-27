package com.reelshort.backend.admin;

import java.util.Set;
import java.util.UUID;

public record CurrentAdmin(UUID adminUserId, String username, Set<String> permissions) {

	public CurrentAdmin {
		permissions = Set.copyOf(permissions);
	}
}
