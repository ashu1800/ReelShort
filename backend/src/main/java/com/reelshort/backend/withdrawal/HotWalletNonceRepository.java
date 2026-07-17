package com.reelshort.backend.withdrawal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface HotWalletNonceRepository extends JpaRepository<HotWalletNonce, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select nonce from HotWalletNonce nonce where nonce.network = :network "
			+ "and nonce.walletAddress = :walletAddress and nonce.chainId = :chainId")
	Optional<HotWalletNonce> findForUpdate(String network, String walletAddress, long chainId);
}
