package com.reelshort.backend.payment;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class PaymentEventLocks {

	private static final int STRIPE_COUNT = 256;

	private final ReentrantLock[] locks = new ReentrantLock[STRIPE_COUNT];

	PaymentEventLocks() {
		for (int index = 0; index < STRIPE_COUNT; index++) {
			locks[index] = new ReentrantLock();
		}
	}

	<T> T withEventLock(String providerEventId, Supplier<T> action) {
		ReentrantLock lock = locks[Math.floorMod(providerEventId.hashCode(), STRIPE_COUNT)];
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
