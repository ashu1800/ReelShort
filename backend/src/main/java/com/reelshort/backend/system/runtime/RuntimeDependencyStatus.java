package com.reelshort.backend.system.runtime;

public record RuntimeDependencyStatus(String name, String status, String detail) {

	public static RuntimeDependencyStatus up(String name, String detail) {
		return new RuntimeDependencyStatus(name, "UP", detail);
	}

	public static RuntimeDependencyStatus down(String name, String detail) {
		return new RuntimeDependencyStatus(name, "DOWN", detail);
	}
}
