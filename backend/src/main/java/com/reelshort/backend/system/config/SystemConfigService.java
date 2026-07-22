package com.reelshort.backend.system.config;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.reelshort.backend.admin.AdminException;

@Service
public class SystemConfigService {
	private static final ReentrantLock CONFIG_UPDATE_LOCK = new ReentrantLock();
	private static final BigDecimal MAX_WITHDRAWAL_USDT = new BigDecimal("999999999999.999999");

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
		if (!changedKey.equals(SystemConfigRegistry.WITHDRAW_USDT_PER_50_POINTS)
				&& !changedKey.equals(SystemConfigRegistry.WITHDRAW_FEE_PERCENT)) {
			return;
		}
		BigDecimal rate = new BigDecimal(configValue(
				SystemConfigRegistry.WITHDRAW_USDT_PER_50_POINTS, changedKey, changedValue));
		int feePercent = Integer.parseInt(configValue(
				SystemConfigRegistry.WITHDRAW_FEE_PERCENT, changedKey, changedValue));
		if (feePercent >= 100) {
			throw new AdminException(400, "withdrawal fee must be below 100 percent");
		}
		long pointAmount = Integer.MAX_VALUE;
		long feeAmount = (pointAmount * feePercent + 99) / 100;
		long withdrawablePoints = pointAmount - feeAmount;
		BigDecimal maximumUsdt = rate.multiply(BigDecimal.valueOf(withdrawablePoints))
				.divide(BigDecimal.valueOf(50), 2, RoundingMode.DOWN);
		if (maximumUsdt.compareTo(MAX_WITHDRAWAL_USDT) > 0) {
			throw new AdminException(400, "withdrawal conversion would overflow usdt amount");
		}
		if (maximumUsdt.compareTo(new BigDecimal("0.01")) < 0) {
			throw new AdminException(400, "withdrawal rate is too small");
		}
	}

	private String configValue(String key, String changedKey, String changedValue) {
		if (key.equals(changedKey)) {
			return changedValue;
		}
		SystemConfigDefinition definition = systemConfigRegistry.definition(key);
		return systemConfigRepository.findById(key)
				.map(SystemConfig::value)
				.orElse(definition.defaultValue());
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

	@Transactional(readOnly = true)
	public String stringValue(String key) {
		SystemConfigDefinition definition = systemConfigRegistry.definition(key);
		return systemConfigRepository.findById(key)
				.map(SystemConfig::value)
				.orElse(definition.defaultValue());
	}
}
