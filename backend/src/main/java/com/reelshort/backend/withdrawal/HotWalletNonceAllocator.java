package com.reelshort.backend.withdrawal;

import java.math.BigInteger;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class HotWalletNonceAllocator {

	private static final int MAX_INITIALIZATION_ATTEMPTS = 3;

	private final HotWalletNonceRepository nonceRepository;
	private final TransactionTemplate transactionTemplate;

	public HotWalletNonceAllocator(HotWalletNonceRepository nonceRepository,
			PlatformTransactionManager transactionManager) {
		this.nonceRepository = nonceRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public void ensureInitialized(String network, String walletAddress, long chainId, BigInteger observedChainNonce) {
		String normalizedWallet = walletAddress.toLowerCase();
		DataIntegrityViolationException lastConflict = null;
		for (int attempt = 0; attempt < MAX_INITIALIZATION_ATTEMPTS; attempt++) {
			try {
				transactionTemplate.executeWithoutResult(status -> initializeInTransaction(
						network, normalizedWallet, chainId, observedChainNonce));
				return;
			}
			catch (DataIntegrityViolationException conflict) {
				lastConflict = conflict;
			}
		}
		throw lastConflict == null
				? new IllegalStateException("nonce allocation failed")
				: lastConflict;
	}

	public BigInteger allocateInitialized(String network, String walletAddress, long chainId,
			BigInteger observedChainNonce) {
		HotWalletNonce nonce = nonceRepository.findForUpdate(network, walletAddress.toLowerCase(), chainId)
				.orElseThrow(() -> new IllegalStateException("nonce row is not initialized"));
		BigInteger allocated = nonce.allocate(observedChainNonce);
		nonceRepository.save(nonce);
		return allocated;
	}

	private void initializeInTransaction(String network, String walletAddress, long chainId,
			BigInteger observedChainNonce) {
		if (nonceRepository.findForUpdate(network, walletAddress, chainId).isEmpty()) {
			nonceRepository.saveAndFlush(HotWalletNonce.create(
					network, walletAddress, chainId, observedChainNonce));
		}
	}
}
