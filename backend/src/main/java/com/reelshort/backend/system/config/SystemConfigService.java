package com.reelshort.backend.system.config;

import java.util.List;
import java.math.BigDecimal;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.reelshort.backend.admin.AdminException;

@Service
public class SystemConfigService {
	private static final ReentrantLock CONFIG_UPDATE_LOCK = new ReentrantLock();

	private final SystemConfigRepository systemConfigRepository;
	private final SystemConfigRegistry systemConfigRegistry;

	public SystemConfigService(SystemConfigRepository systemConfigRepository, SystemConfigRegistry systemConfigRegistry) {
		this.systemConfigRepository = systemConfigRepository;
		this.systemConfigRegistry = systemConfigRegistry;
	}

	@Transactional(readOnly = true)
	public List<SystemConfigResponse> configs() {
		return systemConfigRegistry.definitions().stream()
				.map(definition -> SystemConfigResponse.from(definition,
						systemConfigRepository.findById(definition.key()).orElse(null)))
				.toList();
	}

	@Transactional
	public SystemConfigResponse update(String key, String value) {
		CONFIG_UPDATE_LOCK.lock();
		boolean releaseAfterMethod = true;
		try {
			if (TransactionSynchronizationManager.isSynchronizationActive()) {
				releaseAfterMethod = false;
				TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
					@Override
					public void afterCompletion(int status) {
						CONFIG_UPDATE_LOCK.unlock();
					}
				});
			}
			SystemConfigDefinition definition = systemConfigRegistry.definition(key);
			String validatedValue = definition.validate(value);
			validateWithdrawalCombination(key, validatedValue);
			SystemConfig config = systemConfigRepository.findById(key)
					.orElseGet(() -> SystemConfig.create(key, definition.defaultValue(), definition.description()));
			config.update(validatedValue, definition.description());
			return SystemConfigResponse.from(definition, systemConfigRepository.save(config));
		} finally {
			if (releaseAfterMethod) {
				CONFIG_UPDATE_LOCK.unlock();
			}
		}
	}

	private void validateWithdrawalCombination(String changedKey, String changedValue) {
		if (!changedKey.equals(SystemConfigRegistry.WITHDRAW_CNY_PER_POINT)
				&& !changedKey.equals(SystemConfigRegistry.WITHDRAW_CNY_PER_USD)
				&& !changedKey.equals(SystemConfigRegistry.WITHDRAW_MINIMUM_USD)) {
			return;
		}
		BigDecimal cnyPerPoint = withdrawalDecimal(SystemConfigRegistry.WITHDRAW_CNY_PER_POINT, changedKey,
				changedValue);
		BigDecimal cnyPerUsd = withdrawalDecimal(SystemConfigRegistry.WITHDRAW_CNY_PER_USD, changedKey, changedValue);
		BigDecimal minimumUsd = withdrawalDecimal(SystemConfigRegistry.WITHDRAW_MINIMUM_USD, changedKey, changedValue);
		BigDecimal minimumPoints = minimumUsd.multiply(cnyPerUsd).divide(cnyPerPoint, 0,
				java.math.RoundingMode.CEILING);
		if (minimumPoints.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
			throw new AdminException(400, "withdrawal conversion would overflow point amount");
		}
		BigDecimal maxUsdtAmount = cnyPerPoint.multiply(BigDecimal.valueOf(Integer.MAX_VALUE)).divide(cnyPerUsd, 6,
				java.math.RoundingMode.HALF_UP);
		if (maxUsdtAmount.compareTo(new BigDecimal("999999999999.999999")) > 0) {
			throw new AdminException(400, "withdrawal conversion would overflow usdt amount");
		}
	}

	private BigDecimal withdrawalDecimal(String key, String changedKey, String changedValue) {
		if (key.equals(changedKey)) {
			return new BigDecimal(changedValue);
		}
		SystemConfigDefinition definition = systemConfigRegistry.definition(key);
		return new BigDecimal(systemConfigRepository.findById(key)
				.map(SystemConfig::value)
				.orElse(definition.defaultValue()));
	}

	@Transactional(readOnly = true)
	public int intValue(String key) {
		SystemConfigDefinition definition = systemConfigRegistry.definition(key);
		String value = systemConfigRepository.findById(key)
				.map(SystemConfig::value)
				.orElse(definition.defaultValue());
		return Integer.parseInt(definition.validate(value));
	}

	@Transactional(readOnly = true)
	public BigDecimal decimalValue(String key) {
		SystemConfigDefinition definition = systemConfigRegistry.definition(key);
		String value = systemConfigRepository.findById(key)
				.map(SystemConfig::value)
				.orElse(definition.defaultValue());
		return new BigDecimal(definition.validate(value));
	}
}
