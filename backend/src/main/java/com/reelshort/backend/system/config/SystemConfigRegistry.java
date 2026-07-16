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
	public static final String POINTS_FAIR_MODE_ENABLED = "points.fair-mode.enabled";
	public static final String CONTENT_RECOMMENDATION_STRATEGY = "content.recommendation.strategy";
	public static final String WITHDRAW_CNY_PER_POINT = "withdraw.cny-per-point";
	public static final String WITHDRAW_CNY_PER_USD = "withdraw.cny-per-usd";
	public static final String WITHDRAW_MINIMUM_USD = "withdraw.minimum-usd";
	public static final String WITHDRAW_FEE_PERCENT = "withdraw.fee-percent";
	public static final String VIP_PRICE_USDT = "vip.price-usdt";
	public static final String VIP_FREE_EPISODES = "vip.free-episodes";
	public static final String VIP_COLLECTION_ADDRESS = "vip.collection-address";
	public static final String VIP_ORDER_TIMEOUT_MINUTES = "vip.order-timeout-minutes";

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
			new SystemConfigDefinition(POINTS_FAIR_MODE_ENABLED, "0",
					"Fair mode: points calculated with 1 decimal precision (1=on, 0=off).",
					ValueType.INTEGER, 0, 1, Set.of()),
			new SystemConfigDefinition(WITHDRAW_CNY_PER_POINT, "0.02",
					"CNY value of one point for withdrawals.", ValueType.DECIMAL, 0, 0, Set.of()),
			new SystemConfigDefinition(WITHDRAW_CNY_PER_USD, "7.2",
					"CNY value of one USD for withdrawal conversion.", ValueType.DECIMAL, 0, 0, Set.of()),
			new SystemConfigDefinition(WITHDRAW_MINIMUM_USD, "10",
					"Minimum USD value required for withdrawal.", ValueType.DECIMAL, 0, 0, Set.of()),
			new SystemConfigDefinition(WITHDRAW_FEE_PERCENT, "10",
					"Withdrawal fee percentage deducted from points.", ValueType.INTEGER, 0, 100, Set.of()),
			new SystemConfigDefinition(VIP_PRICE_USDT, "15",
					"VIP monthly subscription price in USDT.", ValueType.DECIMAL, 0, 0, Set.of()),
			new SystemConfigDefinition(VIP_FREE_EPISODES, "7",
					"Number of free episodes viewable without VIP.", ValueType.INTEGER, 1, 100, Set.of()),
			new SystemConfigDefinition(VIP_COLLECTION_ADDRESS, "",
					"TRC20 USDT collection wallet address for VIP payments.", ValueType.STRING, 0, 0, Set.of()),
			new SystemConfigDefinition(VIP_ORDER_TIMEOUT_MINUTES, "20",
					"VIP order payment timeout in minutes.", ValueType.INTEGER, 1, 10_000, Set.of()),
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
