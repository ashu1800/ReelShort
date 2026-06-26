package com.reelshort.backend.watch;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.system.concurrency.UserActionLocks;

@Service
public class WatchService {

	private final WatchRecordRepository watchRecordRepository;
	private final WatchProgressTransaction watchProgressTransaction;
	private final UserActionLocks userActionLocks;

	public WatchService(WatchRecordRepository watchRecordRepository, WatchProgressTransaction watchProgressTransaction,
			UserActionLocks userActionLocks) {
		this.watchRecordRepository = watchRecordRepository;
		this.watchProgressTransaction = watchProgressTransaction;
		this.userActionLocks = userActionLocks;
	}

	public WatchRecordResponse reportProgress(UUID userId, WatchProgressRequest request) {
		return userActionLocks.withUserLock(userId, () -> watchProgressTransaction.reportProgress(userId, request));
	}

	@Transactional(readOnly = true)
	public List<WatchRecordResponse> history(UUID userId) {
		return watchRecordRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
				.map(WatchRecordResponse::from)
				.toList();
	}
}
