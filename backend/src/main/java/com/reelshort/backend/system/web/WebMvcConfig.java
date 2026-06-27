package com.reelshort.backend.system.web;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.reelshort.backend.admin.AdminPermissionInterceptor;
import com.reelshort.backend.admin.CurrentAdminArgumentResolver;
import com.reelshort.backend.auth.CurrentUserArgumentResolver;
import com.reelshort.backend.system.logs.SystemLogProperties;
import com.reelshort.backend.system.ratelimit.RateLimitInterceptor;
import com.reelshort.backend.system.ratelimit.RateLimitProperties;

@Configuration
@EnableConfigurationProperties({RateLimitProperties.class, SystemLogProperties.class})
public class WebMvcConfig implements WebMvcConfigurer {

	private final CurrentUserArgumentResolver currentUserArgumentResolver;
	private final CurrentAdminArgumentResolver currentAdminArgumentResolver;
	private final AdminPermissionInterceptor adminPermissionInterceptor;
	private final ObjectProvider<RateLimitInterceptor> rateLimitInterceptorProvider;

	public WebMvcConfig(CurrentUserArgumentResolver currentUserArgumentResolver,
			CurrentAdminArgumentResolver currentAdminArgumentResolver,
			AdminPermissionInterceptor adminPermissionInterceptor,
			ObjectProvider<RateLimitInterceptor> rateLimitInterceptorProvider) {
		this.currentUserArgumentResolver = currentUserArgumentResolver;
		this.currentAdminArgumentResolver = currentAdminArgumentResolver;
		this.adminPermissionInterceptor = adminPermissionInterceptor;
		this.rateLimitInterceptorProvider = rateLimitInterceptorProvider;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(currentUserArgumentResolver);
		resolvers.add(currentAdminArgumentResolver);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(adminPermissionInterceptor)
				.addPathPatterns("/api/admin/**");
		RateLimitInterceptor rateLimitInterceptor = rateLimitInterceptorProvider.getIfAvailable();
		if (rateLimitInterceptor != null) {
			registry.addInterceptor(rateLimitInterceptor);
		}
	}
}
