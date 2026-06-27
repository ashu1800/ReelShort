package com.reelshort.backend.system.runtime;

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

@Component
public class DatabaseRuntimeDependencyChecker implements RuntimeDependencyChecker {

	private final DataSource dataSource;

	public DatabaseRuntimeDependencyChecker(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public RuntimeDependencyStatus check() {
		try (Connection connection = dataSource.getConnection()) {
			return connection.isValid(1)
					? RuntimeDependencyStatus.up("database", "validated")
					: RuntimeDependencyStatus.down("database", "validation failed");
		}
		catch (Exception exception) {
			return RuntimeDependencyStatus.down("database", "unavailable");
		}
	}
}
