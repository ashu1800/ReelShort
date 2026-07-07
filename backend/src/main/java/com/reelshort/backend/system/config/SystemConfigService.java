package com.reelshort.backend.system.config;

import java.util.List;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemConfigService {

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
		SystemConfigDefinition definition = systemConfigRegistry.definition(key);
		String validatedValue = definition.validate(value);
		SystemConfig config = systemConfigRepository.findById(key)
				.orElseGet(() -> SystemConfig.create(key, definition.defaultValue(), definition.description()));
		config.update(validatedValue, definition.description());
		return SystemConfigResponse.from(definition, systemConfigRepository.save(config));
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
