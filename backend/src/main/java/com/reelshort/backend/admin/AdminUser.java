package com.reelshort.backend.admin;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_users")
public class AdminUser {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 64)
	private String username;

	@Column(nullable = false, length = 120)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private AdminUserStatus status;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "admin_user_roles",
			joinColumns = @JoinColumn(name = "admin_user_id", nullable = false),
			inverseJoinColumns = @JoinColumn(name = "role_id", nullable = false))
	private Set<AdminRole> roles = new HashSet<>();

	@Column(nullable = false)
	private OffsetDateTime createdAt;

	protected AdminUser() {
	}

	private AdminUser(UUID id, String username, String passwordHash, AdminUserStatus status, OffsetDateTime createdAt) {
		this.id = id;
		this.username = username;
		this.passwordHash = passwordHash;
		this.status = status;
		this.createdAt = createdAt;
	}

	public static AdminUser create(String username, String passwordHash, AdminUserStatus status) {
		return new AdminUser(UUID.randomUUID(), username, passwordHash, status, OffsetDateTime.now());
	}

	public UUID id() {
		return id;
	}

	public String username() {
		return username;
	}

	public String passwordHash() {
		return passwordHash;
	}

	public AdminUserStatus status() {
		return status;
	}

	public Set<AdminRole> roles() {
		return Set.copyOf(roles);
	}

	public void assignRole(AdminRole role) {
		roles.add(role);
	}

	public Set<String> permissionCodes() {
		return roles.stream()
				.flatMap(role -> role.permissions().stream())
				.map(AdminPermission::code)
				.collect(Collectors.toSet());
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}

