package com.reelshort.backend.points;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransferRepository extends JpaRepository<PointTransfer, UUID> {

	List<PointTransfer> findBySenderUserIdOrRecipientUserIdOrderByCreatedAtDesc(
			UUID senderUserId, UUID recipientUserId);

	long countBySenderUserIdOrRecipientUserId(UUID senderUserId, UUID recipientUserId);
}
