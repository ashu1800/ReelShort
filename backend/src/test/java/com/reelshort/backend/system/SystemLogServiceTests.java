package com.reelshort.backend.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.reelshort.backend.system.logs.SystemLogProperties;
import com.reelshort.backend.system.logs.SystemLogResponse;
import com.reelshort.backend.system.logs.SystemLogException;
import com.reelshort.backend.system.logs.SystemLogService;

class SystemLogServiceTests {

	@TempDir
	Path tempDir;

	@Test
	void missingLogDirectoryReturnsEmptyResponse() {
		Path missingRoot = tempDir.resolve("missing-logs");
		SystemLogService service = new SystemLogService(new SystemLogProperties(missingRoot, 500, 1_048_576));

		SystemLogResponse response = service.read(null, 200);

		assertThat(response.files()).isEmpty();
		assertThat(response.selectedFile()).isNull();
		assertThat(response.lines()).isEmpty();
		assertThat(response.truncated()).isFalse();
	}

	@Test
	void readsLastRequestedLinesFromAllowedLogFile() throws Exception {
		Path logRoot = tempDir.resolve("logs");
		Files.createDirectories(logRoot);
		Files.write(logRoot.resolve("backend.log"), List.of("line-1", "line-2", "line-3", "line-4"));
		Files.writeString(logRoot.resolve("notes.txt"), "not a log");
		SystemLogService service = new SystemLogService(new SystemLogProperties(logRoot, 500, 1_048_576));

		SystemLogResponse response = service.read("backend.log", 2);

		assertThat(response.files()).containsExactly("backend.log");
		assertThat(response.selectedFile()).isEqualTo("backend.log");
		assertThat(response.lines()).containsExactly("line-3", "line-4");
		assertThat(response.lineCount()).isEqualTo(2);
		assertThat(response.truncated()).isTrue();
		assertThat(response.updatedAt()).isNotNull();
	}

	@Test
	void rejectsUnsafeFileNames() {
		Path logRoot = tempDir.resolve("logs");
		SystemLogService service = new SystemLogService(new SystemLogProperties(logRoot, 500, 1_048_576));

		assertThatThrownBy(() -> service.read("../backend.log", 200))
				.isInstanceOf(SystemLogException.class)
				.hasMessage("invalid log file");
		assertThatThrownBy(() -> service.read("nested/backend.log", 200))
				.isInstanceOf(SystemLogException.class)
				.hasMessage("invalid log file");
		assertThatThrownBy(() -> service.read(tempDir.resolve("backend.log").toString(), 200))
				.isInstanceOf(SystemLogException.class)
				.hasMessage("invalid log file");
		assertThatThrownBy(() -> service.read("backend.txt", 200))
				.isInstanceOf(SystemLogException.class)
				.hasMessage("invalid log file");
	}

	@Test
	void clampsRequestedLineCountToConfiguredMaximum() throws Exception {
		Path logRoot = tempDir.resolve("logs");
		Files.createDirectories(logRoot);
		Files.write(logRoot.resolve("backend.log"), List.of("line-1", "line-2", "line-3"));
		SystemLogService service = new SystemLogService(new SystemLogProperties(logRoot, 2, 1_048_576));

		SystemLogResponse response = service.read("backend.log", 1000);

		assertThat(response.lines()).containsExactly("line-2", "line-3");
		assertThat(response.requestedLines()).isEqualTo(2);
	}

	@Test
	void limitsReadBytesToTailOfLargeFile() throws Exception {
		Path logRoot = tempDir.resolve("logs");
		Files.createDirectories(logRoot);
		Files.writeString(logRoot.resolve("backend.log"), "old-secret-line\nline-1\nline-2\nline-3\n");
		SystemLogService service = new SystemLogService(new SystemLogProperties(logRoot, 500, 22));

		SystemLogResponse response = service.read("backend.log", 10);

		assertThat(response.lines()).containsExactly("line-1", "line-2", "line-3");
		assertThat(response.truncated()).isTrue();
	}
}
