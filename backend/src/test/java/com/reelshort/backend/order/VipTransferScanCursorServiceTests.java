package com.reelshort.backend.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VipTransferScanCursorServiceTests {
	@Autowired
	private VipTransferScanCursorService service;

	@Autowired
	private VipTransferScanCursorRepository repository;

	@Test
	void fingerprintSurvivesIndependentTransactionsAndCompletionClearsCursor() {
		String suffix = UUID.randomUUID().toString();
		String address = "T-address-" + suffix;
		String contract = "T-contract-" + suffix;
		OffsetDateTime window = OffsetDateTime.now().minusMinutes(5);
		VipTransferScanCursorService.State started = service.start(address, contract, window);
		service.advance(started.id(), "page-2");

		VipTransferScanCursorService.State resumed = service.start(address, contract, window);

		assertThat(resumed.id()).isEqualTo(started.id());
		assertThat(resumed.fingerprint()).isEqualTo("page-2");
		service.complete(resumed.id());
		assertThat(repository.findById(resumed.id())).isEmpty();
	}
}
