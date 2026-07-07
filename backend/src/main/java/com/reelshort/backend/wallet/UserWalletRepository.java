package com.reelshort.backend.wallet;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWalletRepository extends JpaRepository<UserWallet, UUID> {

	Optional<UserWallet> findByUserId(UUID userId);
}
