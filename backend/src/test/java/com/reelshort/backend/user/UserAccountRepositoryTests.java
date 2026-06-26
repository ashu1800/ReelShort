package com.reelshort.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class UserAccountRepositoryTests {

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Test
	void usernameIsUnique() {
		userAccountRepository.saveAndFlush(UserAccount.create("ivy", "hash-1", UserStatus.ACTIVE));

		assertThatThrownBy(() -> userAccountRepository.saveAndFlush(
				UserAccount.create("ivy", "hash-2", UserStatus.ACTIVE)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findsUserByUsername() {
		UserAccount user = userAccountRepository.saveAndFlush(
				UserAccount.create("jane", "hash", UserStatus.ACTIVE));

		assertThat(userAccountRepository.findByUsername("jane")).contains(user);
		assertThat(userAccountRepository.existsByUsername("jane")).isTrue();
	}
}
