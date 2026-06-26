package com.reelshort.backend.system.ratelimit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reelshort.rate-limit")
public class RateLimitProperties {

	private boolean enabled = true;
	private List<RuleProperties> rules = new ArrayList<>();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public List<RuleProperties> getRules() {
		return rules;
	}

	public void setRules(List<RuleProperties> rules) {
		this.rules = rules;
	}

	public List<RateLimitRule> toRules() {
		return rules.stream()
				.map(rule -> new RateLimitRule(rule.getName(), rule.getMethod(), rule.getPath(), rule.getLimit(),
						rule.getWindow()))
				.toList();
	}

	public static class RuleProperties {

		private String name;
		private String method;
		private String path;
		private int limit;
		private Duration window;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getMethod() {
			return method;
		}

		public void setMethod(String method) {
			this.method = method;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public int getLimit() {
			return limit;
		}

		public void setLimit(int limit) {
			this.limit = limit;
		}

		public Duration getWindow() {
			return window;
		}

		public void setWindow(Duration window) {
			this.window = window;
		}
	}
}
