package com.reelshort.backend.release;

import java.net.URL;
import java.util.Date;

import org.springframework.stereotype.Service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;

/**
 * Generates short-lived COS pre-signed download URLs on demand. COS credentials are held only
 * server-side and never exposed to clients; clients receive URLs that expire within the configured
 * TTL (default 1h).
 */
@Service
public class CosPresignService {

	private final AppReleaseProperties properties;
	private final COSClient cosClient;

	public CosPresignService(AppReleaseProperties properties) {
		this.properties = properties;
		this.cosClient = buildClient(properties);
	}

	/**
	 * Produce a pre-signed GET URL for the given object key, valid for the configured TTL.
	 *
	 * @throws ReleaseException if COS is not configured (no credentials/bucket) or the key is blank
	 */
	public String presignDownload(String objectKey) {
		if (objectKey == null || objectKey.isBlank()) {
			throw new ReleaseException(500, "object key is required");
		}
		if (cosClient == null) {
			throw new ReleaseException(503, "release download is not configured");
		}
		Date expiration = new Date(System.currentTimeMillis() + properties.getPresignTtl().toMillis());
		URL url = cosClient.generatePresignedUrl(properties.getCosBucket(), objectKey, expiration);
		return url.toString();
	}

	private static COSClient buildClient(AppReleaseProperties properties) {
		String secretId = properties.getCosSecretId();
		String secretKey = properties.getCosSecretKey();
		String bucket = properties.getCosBucket();
		if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()
				|| bucket == null || bucket.isBlank()) {
			return null;
		}
		COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
		ClientConfig clientConfig = new ClientConfig(new Region(properties.getCosRegion()));
		return new COSClient(credentials, clientConfig);
	}
}
