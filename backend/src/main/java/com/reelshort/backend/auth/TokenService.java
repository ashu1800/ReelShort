package com.reelshort.backend.auth;

import com.reelshort.backend.user.UserAccount;
import java.util.UUID;

public interface TokenService {

	AuthToken issue(UserAccount user);

	void revoke(String token);

	void revokeAllForUser(UUID userId);
}
