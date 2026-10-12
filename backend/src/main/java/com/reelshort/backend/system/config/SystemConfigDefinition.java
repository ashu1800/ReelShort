package com.reelshort.backend.system.config;

import java.util.Locale;
import java.math.BigDecimal;
import java.util.Set;
import java.util.function.Function;

import com.reelshort.backend.admin.AdminException;

public record SystemConfigDefinition(
		String key,
		String defaultValue,
		String description,
		ValueType valueType,
		int min,
		int max,
		Set<String> allowedValues,
		Function<String, String> valueValidator) {

	/**
	 * P2-17: 绝大多数配置项沿用内置 INTEGER/DECIMAL/ENUM/STRING 校验，该构造器保持原有 12 个配置项定义不变。
	 */
	public SystemConfigDefinition(String key, String defaultValue, String description, ValueType valueType, int min,
			int max, Set<String> allowedValues) {
		this(key, defaultValue, description, valueType, min, max, allowedValues, null);
	}

	public String validate(String value) {
		String trimmed = value == null ? "" : value.trim();
		if (valueValidator != null) {
			// P2-17: 自定义校验完全接管（例如资金地址允许保存为空，非空则必须通过格式校验）
			return valueValidator.apply(trimmed);
		}
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
				if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
					throw new AdminException(400, "bad request");
				}
				if (parsed.scale() > decimalScale(key) || parsed.compareTo(decimalMaximum(key)) > 0) {
						throw new AdminException(400, "bad request");
					}
				return parsed.stripTrailingZeros().toPlainString();
			}
			catch (NumberFormatException exception) {
				throw new AdminException(400, "bad request");
			}
		}
		if (valueType == ValueType.STRING) {
			return trimmed;
		}
		String normalized = trimmed.toUpperCase(Locale.ROOT);
		if (!allowedValues.contains(normalized)) {
			throw new AdminException(400, "bad request");
		}
		return normalized;
	}

	private int decimalScale(String configKey) {
		return "vip.price-usdt".equals(configKey) ? 6 : 8;
	}

	private BigDecimal decimalMaximum(String configKey) {
		if ("withdraw.usdt-per-50-points".equals(configKey)) {
			return new BigDecimal("9999999999.99999999");
		}
		return BigDecimal.valueOf(100);
	}

	public enum ValueType {
		INTEGER,
		DECIMAL,
		ENUM,
		STRING
	}
}
