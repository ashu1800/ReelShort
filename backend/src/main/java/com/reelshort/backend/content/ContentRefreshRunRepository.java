package com.reelshort.backend.content;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRefreshRunRepository extends JpaRepository<ContentRefreshRun, UUID> {

	List<ContentRefreshRun> findTop10ByOrderByStartedAtDesc();
}
