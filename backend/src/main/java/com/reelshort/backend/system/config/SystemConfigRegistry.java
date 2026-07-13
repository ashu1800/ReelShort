package com.reelshort.backend.system.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.system.config.SystemConfigDefinition.ValueType;

@Component
public class SystemConfigRegistry {

	public static final String POINTS_WATCH_SECONDS_PER_POINT = "points.watch.seconds-per-point";
	public static final String POINTS_DAILY_EARNED_MAXIMUM = "points.daily-earned.maximum";
	public static final String POINTS_DAILY_EARNED_FLUCTUATION_PERCENT = "points.daily-earned.fluctuation-percent";
	public static final String CONTENT_RECOMMENDATION_STRATEGY = "content.recommendation.strategy";
	public static final String WITHDRAW_CNY_PER_POINT = "withdraw.cny-per-point";
	public static final String WITHDRAW_CNY_PER_USD = "withdraw.cny-per-usd";
	public static final String WITHDRAW_MINIMUM_USD = "withdraw.minimum-usd";
	public static final String POINTS_TRANSFER_MINIMUM_POINTS = "points.transfer.minimum-points";

	private final List<SystemConfigDefinition> definitions = List.of(
			new SystemConfigDefinition(POINTS_WATCH_SECONDS_PER_POINT, "60",
					"Completed video seconds required for one watch reward point.",
					ValueType.INTEGER, 1, 86_400, Set.of()),
			new SystemConfigDefinition(POINTS_DAILY_EARNED_MAXIMUM, "1000",
					"Maximum automatically earned points per account per day. Zero disables the limit.",
					ValueType.INTEGER, 0, 1_000_000, Set.of()),
			new SystemConfigDefinition(POINTS_DAILY_EARNED_FLUCTUATION_PERCENT, "35",
					"Maximum random downward fluctuation percentage applied per account and server day.",
					ValueType.INTEGER, 0, 100, Set.of()),
			new SystemConfigDefinition(WITHDRAW_CNY_PER_POINT, "0.02",
					"CNY value of one point for withdrawals.", ValueType.DECIMAL, 0, 0, Set.of()),
			new SystemConfigDefinition(WITHDRAW_CNY_PER_USD, "7.2",
					"CNY value of one USD for withdrawal conversion.", ValueType.DECIMAL, 0, 0, Set.of()),
			new SystemConfigDefinition(WITHDRAW_MINIMUM_USD, "10",
					"Minimum USD value required for withdrawal.", ValueType.DECIMAL, 0, 0, Set.of()),
			new SystemConfigDefinition(POINTS_TRANSFER_MINIMUM_POINTS, "1", "Minimum points required for point transfer.",
					ValueType.INTEGER, 1, 1_000_000, Set.of()),
			new SystemConfigDefinition(CONTENT_RECOMMENDATION_STRATEGY, "LATEST", "Default content recommendation strategy.",
					ValueType.ENUM, 0, 0, Set.of("LATEST", "POPULAR")));

	private final Map<String, SystemConfigDefinition> definitionsByKey = definitions.stream()
			.collect(Collectors.toUnmodifiableMap(SystemConfigDefinition::key, Function.identity()));

	public List<SystemConfigDefinition> definitions() {
		return definitions;
	}

	public SystemConfigDefinition definition(String key) {
		SystemConfigDefinition definition = definitionsByKey.get(key);
		if (definition == null) {
			throw new AdminException(404, "config not found");
		}
		return definition;
	}
}
