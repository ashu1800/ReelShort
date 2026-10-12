package com.reelshort.backend.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * P3-12: X-Request-Id 白名单校验，不合规一律重新生成，避免日志与审计被客户端污染。
 */
class RequestIdFilterTests {

	private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	private final RequestIdFilter filter = new RequestIdFilter();

	@Test
	void keepsWhitelistedClientRequestId() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "abc-123_XY");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> {
		});

		assertThat(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo("abc-123_XY");
		assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("abc-123_XY");
	}

	@Test
	void replacesRequestIdOutsideWhitelist() throws Exception {
		assertGeneratedRequestId("bad id with spaces");
		assertGeneratedRequestId("short");
		assertGeneratedRequestId("a".repeat(65));
		assertGeneratedRequestId("inject\nlog-line");
		assertGeneratedRequestId("../../etc/passwd");
	}

	@Test
	void generatesRequestIdWhenHeaderMissing() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> {
		});

		assertThat(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE).toString()).matches(UUID_PATTERN);
		assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
				.isEqualTo(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
	}

	private void assertGeneratedRequestId(String headerValue) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, headerValue);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> {
		});

		Object applied = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		assertThat(applied).isNotEqualTo(headerValue);
		assertThat(applied.toString()).matches(UUID_PATTERN);
		assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(applied);
	}
}
