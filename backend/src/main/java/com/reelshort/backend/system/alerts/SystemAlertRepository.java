package com.reelshort.backend.system.alerts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemAlertRepository extends JpaRepository<SystemAlert, UUID> {

	Optional<SystemAlert> findByAlertKey(String alertKey);

	List<SystemAlert> findAllByOrderByLastSeenAtDesc();

	List<SystemAlert> findByStatusOrderByLastSeenAtDesc(SystemAlertStatus status);
}
