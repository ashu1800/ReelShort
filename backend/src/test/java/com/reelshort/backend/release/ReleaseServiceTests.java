package com.reelshort.backend.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceTests {

	@Mock
	private AppReleaseRepository releaseRepository;

	@Mock
	private CosPresignService cosPresignService;

	@InjectMocks
	private ReleaseService releaseService;

	@Test
	void latestManifestReturnsPresignedUrlsAndStoredMetadata() {
		AppRelease release = AppRelease.create("0.4.2", 6, "releases/android/ShortLink-v0.4.2.apk",
				"releases/android/ShortLink-v0.4.2.apk.sha256", 12345L, 65L,
				"abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", "ShortLink v0.4.2",
				"Bug fixes", false, 1);
		when(releaseRepository.findTopByOrderByVersionCodeDescPublishedAtDesc()).thenReturn(Optional.of(release));
		when(cosPresignService.presignDownload("releases/android/ShortLink-v0.4.2.apk"))
				.thenReturn("https://cos.example.com/signed-apk?token=abc");
		when(cosPresignService.presignDownload("releases/android/ShortLink-v0.4.2.apk.sha256"))
				.thenReturn("https://cos.example.com/signed-sha?token=def");

		Optional<UpdateManifestResponse> manifest = releaseService.latestManifest();

		assertThat(manifest).isPresent();
		UpdateManifestResponse body = manifest.get();
		assertThat(body.versionName()).isEqualTo("0.4.2");
		assertThat(body.versionCode()).isEqualTo(6);
		assertThat(body.apkUrl()).isEqualTo("https://cos.example.com/signed-apk?token=abc");
		assertThat(body.sha256Url()).isEqualTo("https://cos.example.com/signed-sha?token=def");
		assertThat(body.sizeBytes()).isEqualTo(12345L);
		assertThat(body.sha256SizeBytes()).isEqualTo(65L);
		assertThat(body.apkSha256())
				.isEqualTo("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
		assertThat(body.publishedAt()).isNotBlank();
	}

	@Test
	void latestManifestEmptyWhenNoRelease() {
		when(releaseRepository.findTopByOrderByVersionCodeDescPublishedAtDesc()).thenReturn(Optional.empty());

		assertThat(releaseService.latestManifest()).isEmpty();
	}

	@Test
	void publishCreatesNewReleaseWhenVersionAbsent() {
		PublishReleaseRequest request = new PublishReleaseRequest("0.4.3", 7, "releases/android/ShortLink-v0.4.3.apk",
				"releases/android/ShortLink-v0.4.3.apk.sha256", 20000L, 65L,
				"1111111111111111111111111111111111111111111111111111111111111111", "", "", false, 1);
		when(releaseRepository.findByVersionName("0.4.3")).thenReturn(Optional.empty());
		when(releaseRepository.save(org.mockito.ArgumentMatchers.any(AppRelease.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		AppRelease saved = releaseService.publish(request);

		assertThat(saved.versionName()).isEqualTo("0.4.3");
		assertThat(saved.versionCode()).isEqualTo(7);
		assertThat(saved.title()).isEqualTo("ShortLink v0.4.3");
		assertThat(saved.releaseNotes()).isEqualTo("ShortLink v0.4.3 update.");
	}

	@Test
	void publishRepublishesWhenVersionExists() {
		AppRelease existing = AppRelease.create("0.4.3", 7, "old-key.apk", "old-key.sha256", 100L, 10L,
				"0000000000000000000000000000000000000000000000000000000000000000", "Old", "Old notes", false, 1);
		when(releaseRepository.findByVersionName("0.4.3")).thenReturn(Optional.of(existing));
		when(releaseRepository.save(org.mockito.ArgumentMatchers.any(AppRelease.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		PublishReleaseRequest request = new PublishReleaseRequest("0.4.3", 7, "new-key.apk", "new-key.sha256", 200L,
				20L, "2222222222222222222222222222222222222222222222222222222222222222", "New", "New notes", true, 2);
		AppRelease saved = releaseService.publish(request);

		assertThat(saved.apkObjectKey()).isEqualTo("new-key.apk");
		assertThat(saved.apkSizeBytes()).isEqualTo(200L);
		assertThat(saved.apkSha256())
				.isEqualTo("2222222222222222222222222222222222222222222222222222222222222222");
		assertThat(saved.title()).isEqualTo("New");
		assertThat(saved.mandatory()).isTrue();
		assertThat(saved.minimumVersionCode()).isEqualTo(2L);
	}
}
