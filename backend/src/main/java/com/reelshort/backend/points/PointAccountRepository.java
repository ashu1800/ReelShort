package com.reelshort.backend.points;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface PointAccountRepository extends JpaRepository<PointAccount, UUID> {

	Optional<PointAccount> findByUserId(UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from PointAccount account where account.userId = :userId")
	Optional<PointAccount> findByUserIdForUpdate(UUID userId);

	/** 全部账户整数积分之和；空表时 coalesce 返回 0，避免调用方处理 null。 */
	@Query("select coalesce(sum(account.balance), 0) from PointAccount account")
	Long sumBalance();

	/** 全部账户十分位尾数之和；它本身已是十分位单位，与 {@link #sumBalance()} 合并即总积分。 */
	@Query("select coalesce(sum(account.fractionalPart), 0) from PointAccount account")
	Long sumFractionalPart();
}
