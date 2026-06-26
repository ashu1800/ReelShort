package com.reelshort.backend.system.concurrency;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class UserActionLocks {

	private static final int STRIPE_COUNT = 256;

	private final ReentrantLock[] locks = new ReentrantLock[STRIPE_COUNT];

	public UserActionLocks() {
		for (int index = 0; index < STRIPE_COUNT; index++) {
			locks[index] = new ReentrantLock();
		}
	}

	public <T> T withUserLock(UUID userId, Supplier<T> action) {
		ReentrantLock lock = locks[Math.floorMod(userId.hashCode(), STRIPE_COUNT)];
		lock.lock();
		boolean unlockAfterTransaction = TransactionSynchronizationManager.isSynchronizationActive();
		if (unlockAfterTransaction) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					lock.unlock();
				}
			});
		}
		try {
			return action.get();
		}
		finally {
			if (!unlockAfterTransaction) {
				lock.unlock();
			}
		}
	}
}
