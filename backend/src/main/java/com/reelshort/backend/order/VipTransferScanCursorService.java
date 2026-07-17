package com.reelshort.backend.order;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VipTransferScanCursorService {
	private final VipTransferScanCursorRepository repository;

	public VipTransferScanCursorService(VipTransferScanCursorRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public State start(String address, String contract, OffsetDateTime window) {
		OffsetDateTime normalizedWindow = window.truncatedTo(ChronoUnit.MICROS);
		VipTransferScanCursor cursor = repository.findForUpdate(address, contract)
				.orElseGet(() -> repository.save(VipTransferScanCursor.create(address, contract, normalizedWindow)));
		if (!cursor.scanWindowStartedAt().isEqual(normalizedWindow)) {
			cursor.reset(normalizedWindow);
			repository.save(cursor);
		}
		return new State(cursor.id(), cursor.fingerprint());
	}

	@Transactional
	public void advance(UUID cursorId, String fingerprint) {
		VipTransferScanCursor cursor = repository.findById(cursorId).orElseThrow();
		cursor.advance(fingerprint);
		repository.save(cursor);
	}

	@Transactional
	public void complete(UUID cursorId) {
		repository.deleteById(cursorId);
	}

	public record State(UUID id, String fingerprint) {
	}
}
