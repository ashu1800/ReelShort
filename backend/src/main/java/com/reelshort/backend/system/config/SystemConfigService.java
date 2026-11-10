package com.reelshort.backend.system.config;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
	private final ApplicationEventPublisher eventPublisher;

	public SystemConfigService(SystemConfigRepository systemConfigRepository, SystemConfigRegistry systemConfigRegistry) {
		this(systemConfigRepository, systemConfigRegistry, event -> {
		});
	}

	@Autowired
	public SystemConfigService(SystemConfigRepository systemConfigRepository, SystemConfigRegistry systemConfigRegistry,
			ApplicationEventPublisher eventPublisher) {
		this.systemConfigRepository = systemConfigRepository;
		this.systemConfigRegistry = systemConfigRegistry;
		this.eventPublisher = eventPublisher;
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
			// 值没变就不发事件：内容上游的监听者会清空货架并重抓一轮上游，重复保存不该付这个代价。
			boolean changed = !validatedValue.equals(config.value());
			config.update(validatedValue, definition.description());
			SystemConfigResponse response = SystemConfigResponse.from(definition, systemConfigRepository.save(config));
			if (changed) {
				publishAfterCommit(key, validatedValue);
			}
			return response;
		} finally {
			if (releaseAfterMethod) {
				CONFIG_UPDATE_LOCK.unlock();
			}
		}
	}

	/**
	 * 配置变更事件在事务提交后再发：回滚的更新不该让内容缓存被清空、让上游被切走。
	 */
	private void publishAfterCommit(String key, String value) {
		Runnable publish = () -> eventPublisher.publishEvent(new SystemConfigChangedEvent(key, value));
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			publish.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				publish.run();
			}
		});
	}

	private void validateWithdrawalCombination(String changedKey, String changedValue) {
		if (!changedKey.equals(SystemConfigRegistry.WITHDRAW_USDT_PER_50_POINTS)
				&& !changedKey.equals(SystemConfigRegistry.WITHDRAW_MINIMUM_USDT)
				&& !changedKey.equals(SystemConfigRegistry.WITHDRAW_FEE_PERCENT)) {
			return;
		}
		BigDecimal rate = new BigDecimal(configValue(
				SystemConfigRegistry.WITHDRAW_USDT_PER_50_POINTS, changedKey, changedValue));
		BigDecimal minimumUsdt = new BigDecimal(configValue(
				SystemConfigRegistry.WITHDRAW_MINIMUM_USDT, changedKey, changedValue));
		int feePercent = Integer.parseInt(configValue(
				SystemConfigRegistry.WITHDRAW_FEE_PERCENT, changedKey, changedValue));
		if (feePercent >= 100) {
			throw new AdminException(400, "withdrawal fee must be below 100 percent");
		}
		// 手续费按到账 USDT 收取，积分不会被多扣，所以上限金额就是全部积分折算后的总额。
		BigDecimal maximumUsdt = rate.multiply(BigDecimal.valueOf(Integer.MAX_VALUE))
				.divide(BigDecimal.valueOf(50), 2, RoundingMode.DOWN);
		if (maximumUsdt.compareTo(MAX_WITHDRAWAL_USDT) > 0) {
			throw new AdminException(400, "withdrawal conversion would overflow usdt amount");
		}
		if (maximumUsdt.compareTo(minimumUsdt) < 0) {
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
