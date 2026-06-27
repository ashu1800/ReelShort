package com.reelshort.backend.admin;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminPermissionRepository extends JpaRepository<AdminPermission, UUID> {

	Optional<AdminPermission> findByCode(String code);
}
