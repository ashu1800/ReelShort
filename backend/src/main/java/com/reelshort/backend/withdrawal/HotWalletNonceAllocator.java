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

	public BigInteger allocate(String network, String walletAddress, long chainId, BigInteger observedChainNonce) {
		String normalizedWallet = walletAddress.toLowerCase();
		DataIntegrityViolationException lastConflict = null;
		for (int attempt = 0; attempt < MAX_INITIALIZATION_ATTEMPTS; attempt++) {
			try {
				return transactionTemplate.execute(status -> allocateInTransaction(
						network, normalizedWallet, chainId, observedChainNonce));
			}
			catch (DataIntegrityViolationException conflict) {
				lastConflict = conflict;
			}
		}
		throw lastConflict == null
				? new IllegalStateException("nonce allocation failed")
				: lastConflict;
	}

	private BigInteger allocateInTransaction(String network, String walletAddress, long chainId,
			BigInteger observedChainNonce) {
		HotWalletNonce nonce = nonceRepository.findForUpdate(network, walletAddress, chainId)
				.orElseGet(() -> nonceRepository.saveAndFlush(
						HotWalletNonce.create(network, walletAddress, chainId, observedChainNonce)));
		BigInteger allocated = nonce.allocate(observedChainNonce);
		nonceRepository.saveAndFlush(nonce);
		return allocated;
	}
}
