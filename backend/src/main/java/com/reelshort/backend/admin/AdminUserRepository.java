package com.reelshort.backend.admin;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

	@EntityGraph(attributePaths = { "roles", "roles.permissions" })
	Optional<AdminUser> findByUsername(String username);

	@EntityGraph(attributePaths = { "roles", "roles.permissions" })
	Optional<AdminUser> findWithRolesById(UUID id);
}
