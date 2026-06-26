package com.reelshort.backend.system;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/system")
public class SystemHealthController {

	@GetMapping("/health")
	public ApiResponse<Map<String, Object>> health(HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(Map.of(
				"status", "UP",
				"service", "reelshort-backend",
				"timestamp", OffsetDateTime.now().toString()), requestId);
	}
}

