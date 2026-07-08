package com.reelshort.backend.system.config;

import java.util.Locale;
import java.math.BigDecimal;
import java.util.Set;

import com.reelshort.backend.admin.AdminException;

public record SystemConfigDefinition(
		String key,
		String defaultValue,
		String description,
		ValueType valueType,
		int min,
		int max,
		Set<String> allowedValues) {

	public String validate(String value) {
		String trimmed = value == null ? "" : value.trim();
		if (trimmed.isBlank()) {
			throw new AdminException(400, "bad request");
		}
		if (valueType == ValueType.INTEGER) {
			try {
				int parsed = Integer.parseInt(trimmed);
				if (parsed < min || parsed > max) {
					throw new AdminException(400, "bad request");
				}
				return String.valueOf(parsed);
			}
			catch (NumberFormatException exception) {
				throw new AdminException(400, "bad request");
			}
		}
		if (valueType == ValueType.DECIMAL) {
			try {
				BigDecimal parsed = new BigDecimal(trimmed);
				if (parsed.compareTo(BigDecimal.ZERO) < 0) {
					throw new AdminException(400, "bad request");
				}
				if ("withdraw.usdt-per-point".equals(key)) {
					if (parsed.scale() > 8 || parsed.compareTo(BigDecimal.valueOf(100)) > 0) {
						throw new AdminException(400, "bad request");
					}
				}
				return parsed.stripTrailingZeros().toPlainString();
			}
			catch (NumberFormatException exception) {
				throw new AdminException(400, "bad request");
			}
		}
		String normalized = trimmed.toUpperCase(Locale.ROOT);
		if (!allowedValues.contains(normalized)) {
			throw new AdminException(400, "bad request");
		}
		return normalized;
	}

	public enum ValueType {
		INTEGER,
		DECIMAL,
		ENUM
	}
}
