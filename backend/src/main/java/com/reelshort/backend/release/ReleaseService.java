package com.reelshort.backend.release;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseService {

	private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

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

	private UpdateManifestResponse toManifest(AppRelease release) {
		return new UpdateManifestResponse(release.versionName(), release.versionCode(), release.title(),
				release.releaseNotes(), release.publishedAt().format(ISO_UTC),
				cosPresignService.presignDownload(release.apkObjectKey()),
				cosPresignService.presignDownload(release.sha256ObjectKey()), release.apkSizeBytes(),
				release.sha256SizeBytes(), release.apkSha256(), release.minimumVersionCode(), release.mandatory());
	}
}
