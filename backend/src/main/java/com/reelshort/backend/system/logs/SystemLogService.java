package com.reelshort.backend.system.logs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SystemLogService {

	private final SystemLogProperties properties;

	public SystemLogService(SystemLogProperties properties) {
		this.properties = properties;
	}

	public SystemLogResponse read(String fileName, Integer lines) {
		Path root = properties.root().toAbsolutePath().normalize();
		if (fileName != null && !fileName.isBlank() && !isSafeLogFileName(fileName)) {
			throw invalidLogFile();
		}
		List<String> files = listLogFiles(root);
		if (files.isEmpty()) {
			return new SystemLogResponse(List.of(), null, clampLines(lines), 0, false, null, List.of());
		}

		String selectedFile = selectFile(fileName, files);
		Path selectedPath = resolveLogPath(root, selectedFile);
		int requestedLines = clampLines(lines);
		TailContent tailContent = readTailContent(selectedPath);
		List<String> allLines = tailContent.lines();
		int fromIndex = Math.max(0, allLines.size() - requestedLines);
		List<String> tail = List.copyOf(allLines.subList(fromIndex, allLines.size()));
		boolean truncated = tailContent.truncated() || fromIndex > 0;
		return new SystemLogResponse(files, selectedFile, requestedLines, tail.size(), truncated,
				lastModified(selectedPath), tail);
	}

	private List<String> listLogFiles(Path root) {
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		try (var stream = Files.list(root)) {
			return stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
					.map(path -> path.getFileName().toString())
					.filter(this::isSafeLogFileName)
					.sorted(Comparator.naturalOrder())
					.toList();
		}
		catch (IOException ex) {
			return List.of();
		}
	}

	private String selectFile(String fileName, List<String> files) {
		if (fileName == null || fileName.isBlank()) {
			return files.get(0);
		}
		if (!isSafeLogFileName(fileName)) {
			throw invalidLogFile();
		}
		if (!files.contains(fileName)) {
			throw invalidLogFile();
		}
		return fileName;
	}

	private Path resolveLogPath(Path root, String fileName) {
		try {
			Path resolved = root.resolve(fileName).normalize();
			if (!resolved.startsWith(root) || !Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
				throw invalidLogFile();
			}
			return resolved;
		}
		catch (InvalidPathException ex) {
			throw new SystemLogException(HttpStatus.BAD_REQUEST.value(), "invalid log file", ex);
		}
	}

	private boolean isSafeLogFileName(String fileName) {
		try {
			Path path = Path.of(fileName);
			return !path.isAbsolute()
					&& path.getNameCount() == 1
					&& fileName.endsWith(".log")
					&& !fileName.contains("..");
		}
		catch (InvalidPathException ex) {
			return false;
		}
	}

	private int clampLines(Integer lines) {
		int requested = lines == null ? 200 : lines;
		if (requested < 1) {
			requested = 1;
		}
		return Math.min(requested, properties.maxLines());
	}

	private TailContent readTailContent(Path path) {
		try {
			long fileSize = Files.size(path);
			long bytesToRead = Math.min(fileSize, properties.maxBytes());
			byte[] buffer;
			try (var input = Files.newInputStream(path)) {
				long skipped = input.skip(fileSize - bytesToRead);
				while (skipped < fileSize - bytesToRead) {
					long next = input.skip(fileSize - bytesToRead - skipped);
					if (next <= 0) {
						break;
					}
					skipped += next;
				}
				buffer = input.readNBytes((int) bytesToRead);
			}
			String content = new String(buffer, StandardCharsets.UTF_8);
			List<String> lines = new ArrayList<>(content.lines().toList());
			if (fileSize > bytesToRead && content.startsWith("\n") && !lines.isEmpty()
					&& lines.get(0).isEmpty()) {
				lines.remove(0);
			}
			else if (fileSize > bytesToRead && !content.startsWith("\n") && !lines.isEmpty()) {
				lines.remove(0);
			}
			return new TailContent(List.copyOf(lines), fileSize > bytesToRead);
		}
		catch (IOException ex) {
			return new TailContent(List.of(), false);
		}
	}

	private String lastModified(Path path) {
		try {
			return Files.getLastModifiedTime(path).toInstant().toString();
		}
		catch (IOException ex) {
			return Instant.EPOCH.toString();
		}
	}

	private SystemLogException invalidLogFile() {
		return new SystemLogException(HttpStatus.BAD_REQUEST.value(), "invalid log file");
	}

	private record TailContent(List<String> lines, boolean truncated) {
	}
}
