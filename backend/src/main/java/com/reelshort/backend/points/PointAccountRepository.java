package com.reelshort.backend.points;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PointAccountRepository extends JpaRepository<PointAccount, UUID> {

	Optional<PointAccount> findByUserId(UUID userId);
}
