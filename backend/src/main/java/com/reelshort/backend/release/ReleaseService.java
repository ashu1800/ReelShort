package com.reelshort.backend.release;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseService {

	private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
	private static final String LEGACY_DOWNLOAD_BASE_URL = "https://shortlink.hjj888.cc/downloads/android/";
	private static final String RELEASE_OBJECT_PREFIX = "releases/android/";

	private final AppReleaseRepository releaseRepository;
	private final CosPresignService cosPresignService;

	public ReleaseService(AppReleaseRepository releaseRepository, CosPresignService cosPresignService) {
		this.releaseRepository = releaseRepository;
		this.cosPresignService = cosPresignService;
	}

	@Transactional
	public AppRelease publish(PublishReleaseRequest request) {
		String versionName = request.versionName().trim();
		String title = request.title() == null || request.title().isBlank()
				? "ShortLink v" + versionName
				: request.title().trim();
		String releaseNotes = request.releaseNotes() == null || request.releaseNotes().isBlank()
				? "ShortLink v" + versionName + " update."
				: request.releaseNotes().trim();

		Optional<AppRelease> existing = releaseRepository.findByVersionName(versionName);
		AppRelease release;
		if (existing.isPresent()) {
			release = existing.get();
			release.republish(request.apkObjectKey(), request.sha256ObjectKey(), request.apkSizeBytes(),
					request.sha256SizeBytes(), request.apkSha256(), title, releaseNotes, request.mandatory(),
					request.minimumVersionCode());
		} else {
			release = AppRelease.create(versionName, request.versionCode(), request.apkObjectKey(),
					request.sha256ObjectKey(), request.apkSizeBytes(), request.sha256SizeBytes(), request.apkSha256(),
					title, releaseNotes, request.mandatory(), request.minimumVersionCode());
		}
		return releaseRepository.save(release);
	}

	public Optional<UpdateManifestResponse> latestManifest() {
		return releaseRepository.findTopByOrderByVersionCodeDescPublishedAtDesc().map(this::toManifest);
	}

	public Optional<UpdateManifestResponse> latestLegacyManifest() {
		return releaseRepository.findTopByOrderByVersionCodeDescPublishedAtDesc().map(this::toLegacyManifest);
	}

	public Optional<String> legacyAssetDownloadUrl(String fileName) {
		if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
			return Optional.empty();
		}
		String objectKey = RELEASE_OBJECT_PREFIX + fileName;
		return releaseRepository.findByApkObjectKeyOrSha256ObjectKey(objectKey, objectKey)
				.flatMap(release -> {
					if (release.apkObjectKey().equals(objectKey)) {
						return Optional.of(cosPresignService.presignDownload(release.apkObjectKey()));
					}
					if (release.sha256ObjectKey().equals(objectKey)) {
						return Optional.of(cosPresignService.presignDownload(release.sha256ObjectKey()));
					}
					return Optional.empty();
				});
	}

	private UpdateManifestResponse toManifest(AppRelease release) {
		return new UpdateManifestResponse(release.versionName(), release.versionCode(), release.title(),
				release.releaseNotes(), release.publishedAt().format(ISO_UTC),
				cosPresignService.presignDownload(release.apkObjectKey()),
				cosPresignService.presignDownload(release.sha256ObjectKey()), release.apkSizeBytes(),
				release.sha256SizeBytes(), release.apkSha256(), release.minimumVersionCode(), release.mandatory());
	}

	private UpdateManifestResponse toLegacyManifest(AppRelease release) {
		return new UpdateManifestResponse(release.versionName(), release.versionCode(), release.title(),
				release.releaseNotes(), release.publishedAt().format(ISO_UTC),
				legacyDownloadUrl(release.apkObjectKey()), legacyDownloadUrl(release.sha256ObjectKey()),
				release.apkSizeBytes(), release.sha256SizeBytes(), release.apkSha256(), release.minimumVersionCode(),
				release.mandatory());
	}

	private String legacyDownloadUrl(String objectKey) {
		int separator = objectKey.lastIndexOf('/');
		String fileName = separator >= 0 ? objectKey.substring(separator + 1) : objectKey;
		return LEGACY_DOWNLOAD_BASE_URL + fileName;
	}
}
