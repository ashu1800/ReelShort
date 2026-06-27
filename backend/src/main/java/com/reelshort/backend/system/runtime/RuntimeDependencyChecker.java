package com.reelshort.backend.system.runtime;

@FunctionalInterface
public interface RuntimeDependencyChecker {

	RuntimeDependencyStatus check();
}
