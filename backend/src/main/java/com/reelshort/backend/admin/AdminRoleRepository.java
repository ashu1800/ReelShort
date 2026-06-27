package com.reelshort.backend.admin;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRoleRepository extends JpaRepository<AdminRole, UUID> {

	@EntityGraph(attributePaths = "permissions")
	Optional<AdminRole> findByCode(String code);
}
