package com.reelshort.backend.auth;

import com.reelshort.backend.user.UserAccount;

public interface TokenService {

	AuthToken issue(UserAccount user);
}
