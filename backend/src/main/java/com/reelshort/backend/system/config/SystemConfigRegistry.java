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

	public static final String POINTS_WATCH_STAGE_POINTS = "points.watch.stage-points";
	public static final String CONTENT_RECOMMENDATION_STRATEGY = "content.recommendation.strategy";

	private final List<SystemConfigDefinition> definitions = List.of(
			new SystemConfigDefinition(POINTS_WATCH_STAGE_POINTS, "1", "Points awarded for each watch progress stage.",
					ValueType.INTEGER, 0, 1000, Set.of()),
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
