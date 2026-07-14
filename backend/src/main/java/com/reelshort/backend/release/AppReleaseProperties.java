package com.reelshort.backend.release;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reelshort.release")
public class AppReleaseProperties {

	private String cosSecretId = "";

	private String cosSecretKey = "";

	private String cosRegion = "ap-chengdu";

	private String cosBucket = "";

	private Duration presignTtl = Duration.ofHours(1);

	public String getCosSecretId() {
		return cosSecretId;
	}

	public void setCosSecretId(String cosSecretId) {
		this.cosSecretId = cosSecretId;
	}

	public String getCosSecretKey() {
		return cosSecretKey;
	}

	public void setCosSecretKey(String cosSecretKey) {
		this.cosSecretKey = cosSecretKey;
	}

	public String getCosRegion() {
		return cosRegion;
	}

	public void setCosRegion(String cosRegion) {
		this.cosRegion = cosRegion;
	}

	public String getCosBucket() {
		return cosBucket;
	}

	public void setCosBucket(String cosBucket) {
		this.cosBucket = cosBucket;
	}

	public Duration getPresignTtl() {
		return presignTtl;
	}

	public void setPresignTtl(Duration presignTtl) {
		this.presignTtl = presignTtl;
	}
}
