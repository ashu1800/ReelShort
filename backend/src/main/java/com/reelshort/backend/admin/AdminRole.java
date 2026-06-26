package com.reelshort.backend.admin;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class AdminRole {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 64)
	private String code;

	@Column(nullable = false, length = 120)
	private String name;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "role_permissions",
			joinColumns = @JoinColumn(name = "role_id", nullable = false),
			inverseJoinColumns = @JoinColumn(name = "permission_id", nullable = false))
	private Set<AdminPermission> permissions = new HashSet<>();

	@Column(nullable = false)
	private OffsetDateTime createdAt;

	protected AdminRole() {
	}

	private AdminRole(UUID id, String code, String name, OffsetDateTime createdAt) {
		this.id = id;
		this.code = code;
		this.name = name;
		this.createdAt = createdAt;
	}

	public static AdminRole create(String code, String name) {
		return new AdminRole(UUID.randomUUID(), code, name, OffsetDateTime.now());
	}

	public UUID id() {
		return id;
	}

	public String code() {
		return code;
	}

	public String name() {
		return name;
	}

	public Set<AdminPermission> permissions() {
		return Set.copyOf(permissions);
	}

	public void grant(AdminPermission permission) {
		permissions.add(permission);
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}

