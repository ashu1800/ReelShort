package com.reelshort.backend.system.config;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_configs")
public class SystemConfig {

	@Id
	@Column(name = "config_key", length = 128)
	private String key;

	@Column(name = "config_value", nullable = false, length = 512)
	private String value;

	@Column(nullable = false, length = 255)
	private String description;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected SystemConfig() {
	}

	private SystemConfig(String key, String value, String description, OffsetDateTime updatedAt) {
		this.key = key;
		this.value = value;
		this.description = description;
		this.updatedAt = updatedAt;
	}

	public static SystemConfig create(String key, String value, String description) {
		return new SystemConfig(key, value, description, OffsetDateTime.now());
	}

	public void update(String value, String description) {
		this.value = value;
		this.description = description;
		this.updatedAt = OffsetDateTime.now();
	}

	public String key() {
		return key;
	}

	public String value() {
		return value;
	}

	public String description() {
		return description;
	}

	public OffsetDateTime updatedAt() {
		return updatedAt;
	}
}
