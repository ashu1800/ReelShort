package com.reelshort.backend.admin;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class AdminPermission {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 64)
	private String code;

	@Column(nullable = false, length = 255)
	private String description;

	protected AdminPermission() {
	}

	private AdminPermission(UUID id, String code, String description) {
		this.id = id;
		this.code = code;
		this.description = description;
	}

	public static AdminPermission create(String code, String description) {
		return new AdminPermission(UUID.randomUUID(), code, description);
	}

	public UUID id() {
		return id;
	}

	public String code() {
		return code;
	}

	public String description() {
		return description;
	}
}
