package com.reelshort.backend.content;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reelshort.content.refresh")
public class ContentRefreshProperties {

	private boolean enabled = true;
	private List<String> shelves = new ArrayList<>(List.of(ContentShelfType.RECOMMEND.apiValue()));
	private List<String> locales = new ArrayList<>(List.of(ContentLocale.ENGLISH.apiValue()));
	private boolean descriptionBackfillEnabled = true;
	private int descriptionBackfillLimit = 150;
	private java.time.Duration descriptionBackfillPause = java.time.Duration.ofMillis(1400);

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public List<String> getShelves() {
		return shelves;
	}

	public void setShelves(List<String> shelves) {
		this.shelves = shelves;
	}

	public boolean isDescriptionBackfillEnabled() {
		return descriptionBackfillEnabled;
	}

	public void setDescriptionBackfillEnabled(boolean descriptionBackfillEnabled) {
		this.descriptionBackfillEnabled = descriptionBackfillEnabled;
	}

	public int getDescriptionBackfillLimit() {
		return descriptionBackfillLimit;
	}

	public void setDescriptionBackfillLimit(int descriptionBackfillLimit) {
		this.descriptionBackfillLimit = descriptionBackfillLimit;
	}

	public java.time.Duration getDescriptionBackfillPause() {
		return descriptionBackfillPause;
	}

	public void setDescriptionBackfillPause(java.time.Duration descriptionBackfillPause) {
		this.descriptionBackfillPause = descriptionBackfillPause;
	}

	public List<String> getLocales() {
		return locales;
	}

	public void setLocales(List<String> locales) {
		this.locales = locales;
	}
}
