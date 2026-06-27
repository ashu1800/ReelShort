package com.reelshort.backend.admin;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AdminPermissions {

	public static final String USER_READ = "USER_READ";
	public static final String USER_WRITE = "USER_WRITE";
	public static final String POINTS_ADJUST = "POINTS_ADJUST";
	public static final String AUDIT_READ = "AUDIT_READ";
	public static final String CONTENT_CACHE_READ = "CONTENT_CACHE_READ";
	public static final String CONTENT_CACHE_WRITE = "CONTENT_CACHE_WRITE";
	public static final String SYSTEM_CONFIG_READ = "SYSTEM_CONFIG_READ";
	public static final String SYSTEM_CONFIG_WRITE = "SYSTEM_CONFIG_WRITE";
	public static final String ORDER_READ = "ORDER_READ";

	public static final Set<String> ALL = Set.copyOf(new LinkedHashSet<>(Set.of(
			USER_READ,
			USER_WRITE,
			POINTS_ADJUST,
			AUDIT_READ,
			CONTENT_CACHE_READ,
			CONTENT_CACHE_WRITE,
			SYSTEM_CONFIG_READ,
			SYSTEM_CONFIG_WRITE,
			ORDER_READ)));

	private AdminPermissions() {
	}
}
