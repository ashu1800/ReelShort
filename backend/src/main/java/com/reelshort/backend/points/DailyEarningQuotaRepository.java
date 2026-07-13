package com.reelshort.backend.points;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface DailyEarningQuotaRepository extends JpaRepository<DailyEarningQuota, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select quota from DailyEarningQuota quota where quota.userId = :userId and quota.earningDate = :earningDate")
	Optional<DailyEarningQuota> findForUpdate(UUID userId, LocalDate earningDate);

	Optional<DailyEarningQuota> findByUserIdAndEarningDate(UUID userId, LocalDate earningDate);
}
