package com.reelshort.backend.admin;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.points.PointTransactionRepository;
import com.reelshort.backend.points.PointTransactionResponse;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;
import com.reelshort.backend.watch.WatchRecordRepository;
import com.reelshort.backend.watch.WatchRecordResponse;

@Service
public class AdminUserService {

	private final UserAccountRepository userAccountRepository;
	private final PointAccountRepository pointAccountRepository;
	private final PointTransactionRepository pointTransactionRepository;
	private final WatchRecordRepository watchRecordRepository;

	public AdminUserService(UserAccountRepository userAccountRepository, PointAccountRepository pointAccountRepository,
			PointTransactionRepository pointTransactionRepository, WatchRecordRepository watchRecordRepository) {
		this.userAccountRepository = userAccountRepository;
		this.pointAccountRepository = pointAccountRepository;
		this.pointTransactionRepository = pointTransactionRepository;
		this.watchRecordRepository = watchRecordRepository;
	}

	@Transactional(readOnly = true)
	public List<AdminUserSummaryResponse> users() {
		return userAccountRepository.findAll().stream()
				.sorted(Comparator.comparing(UserAccount::createdAt).reversed())
				.map(user -> new AdminUserSummaryResponse(user.id(), user.username(), user.status(),
						pointBalance(user.id()), user.createdAt().toString()))
				.toList();
	}

	@Transactional(readOnly = true)
	public AdminUserDetailResponse detail(UUID userId) {
		UserAccount user = user(userId);
		return detailResponse(user);
	}

	@Transactional
	public AdminUserDetailResponse changeStatus(UUID userId, UserStatus status) {
		UserAccount user = user(userId);
		user.changeStatus(status);
		return detailResponse(userAccountRepository.save(user));
	}

	@Transactional(readOnly = true)
	public List<WatchRecordResponse> watchRecords(UUID userId) {
		user(userId);
		return watchRecordRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
				.map(WatchRecordResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<PointTransactionResponse> pointRecords(UUID userId) {
		user(userId);
		return pointTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(PointTransactionResponse::from)
				.toList();
	}

	private AdminUserDetailResponse detailResponse(UserAccount user) {
		return new AdminUserDetailResponse(user.id(), user.username(), user.status(), pointBalance(user.id()),
				watchRecordRepository.countByUserId(user.id()), pointTransactionRepository.countByUserId(user.id()),
				user.createdAt().toString());
	}

	private UserAccount user(UUID userId) {
		return userAccountRepository.findById(userId)
				.orElseThrow(() -> new AdminException(404, "user not found"));
	}

	private int pointBalance(UUID userId) {
		return pointAccountRepository.findByUserId(userId)
				.map(account -> account.balance())
				.orElse(0);
	}
}
