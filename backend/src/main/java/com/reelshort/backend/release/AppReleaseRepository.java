package com.reelshort.backend.release;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppReleaseRepository extends JpaRepository<AppRelease, UUID> {

	Optional<AppRelease> findTopByOrderByVersionCodeDescPublishedAtDesc();

	Optional<AppRelease> findByVersionName(String versionName);

	Optional<AppRelease> findByApkObjectKeyOrSha256ObjectKey(String apkObjectKey, String sha256ObjectKey);
}
