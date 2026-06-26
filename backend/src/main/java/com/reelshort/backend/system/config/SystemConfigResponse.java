package com.reelshort.backend.system.config;

public record SystemConfigResponse(
		String key,
		String value,
		String description,
		String updatedAt) {

	public static SystemConfigResponse from(SystemConfigDefinition definition, SystemConfig config) {
		if (config == null) {
			return new SystemConfigResponse(definition.key(), definition.defaultValue(), definition.description(), null);
		}
		return new SystemConfigResponse(config.key(), config.value(), config.description(), config.updatedAt().toString());
	}
}
