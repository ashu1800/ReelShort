package com.reelshort.backend.order;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface VipTransferScanCursorRepository extends JpaRepository<VipTransferScanCursor, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM VipTransferScanCursor c WHERE c.receivingWalletAddress = :address "
			+ "AND c.tokenContractAddress = :contract")
	Optional<VipTransferScanCursor> findForUpdate(@Param("address") String address,
			@Param("contract") String contract);
}
