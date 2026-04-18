/*
 * Copyright (C) 2016-2020 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.shio.spring;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import com.viglet.shio.utils.ShStaticFileUtils;

/**
 * @author Alexandre Oliveira
 */
@Configuration
@AutoConfigureAfter(DispatcherServletAutoConfiguration.class)
public class ShStaticResourceConfiguration implements WebMvcConfigurer {

	private static final String FORWARD_INDEX_HTML = "forward:/index.html";
	private static final String THIRDPARTY_FOLDER = "/thirdparty/**";

	private final ShStaticFileUtils shStaticFileUtils;
	@Value("${shio.allowedOrigins:localhost}")
	private String allowedOrigins;

	public ShStaticResourceConfiguration(ShStaticFileUtils shStaticFileUtils) {
		this.shStaticFileUtils = shStaticFileUtils;
	}

	@Override
	public void addResourceHandlers(@NotNull ResourceHandlerRegistry registry) {

		String fileSourceLocation = "file:" + shStaticFileUtils.getFileSource().getAbsolutePath() + "/";
		String storeLocation = "file:" + shStaticFileUtils.getFileSource().getParentFile().getAbsolutePath() + "/";
		registry.addResourceHandler("/store/**").addResourceLocations(storeLocation).setCachePeriod(3600 * 24);
		registry.addResourceHandler("/file_source/**").addResourceLocations(fileSourceLocation)
				.setCachePeriod(3600 * 24);

		if (!registry.hasMappingForPattern(THIRDPARTY_FOLDER)) {
			registry.addResourceHandler(THIRDPARTY_FOLDER)
					.addResourceLocations("classpath:/META-INF/resources/webjars/").setCachePeriod(3600 * 24);
		}

		// Universal Editor bridge assets
		registry.addResourceHandler("/universal-editor/**")
				.addResourceLocations("classpath:/universal-editor/")
				.setCachePeriod(3600);

		// React SPA routing: serve index.html for all unmatched paths under these contexts
		registryReact(registry, "/console");
		registryReact(registry, "/login");
		// Tenant-prefixed console: /{tenant}/console/**
		registryReact(registry, "/*/console");
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping(THIRDPARTY_FOLDER).allowedOrigins(allowedOrigins)
				.allowedMethods("PUT", "DELETE", "GET", "POST").allowCredentials(false).maxAge(3600);
		registry.addMapping("/api/**")
				.allowedOriginPatterns(allowedOrigins, "http://localhost:*")
				.allowedMethods("PUT", "DELETE", "GET", "POST", "PATCH", "OPTIONS")
				.allowedHeaders("*")
				.exposedHeaders("X-XSRF-TOKEN")
				.allowCredentials(true)
				.maxAge(3600);
	}

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		// Root redirect to React console
		registry.addRedirectViewController("/", "/console");

		// React SPA entry points
		registry.addViewController("/console").setViewName(FORWARD_INDEX_HTML);
		registry.addViewController("/console/").setViewName(FORWARD_INDEX_HTML);
		// Tenant-prefixed console entry points (tenant is the first path segment)
		registry.addViewController("/*/console").setViewName(FORWARD_INDEX_HTML);
		registry.addViewController("/*/console/").setViewName(FORWARD_INDEX_HTML);
		registry.addViewController("/login").setViewName(FORWARD_INDEX_HTML);
		registry.addViewController("/login/").setViewName(FORWARD_INDEX_HTML);
		registry.addViewController("/setup").setViewName(FORWARD_INDEX_HTML);
		registry.addViewController("/setup/").setViewName(FORWARD_INDEX_HTML);

		// Legacy AngularJS apps
		registry.addViewController("/content").setViewName("forward:/content/index.html");
		registry.addViewController("/content/").setViewName("forward:/content/index.html");
		registry.addViewController("/welcome").setViewName("forward:/welcome/index.html");
		registry.addViewController("/welcome/").setViewName("forward:/welcome/index.html");
		registry.addViewController("/preview").setViewName("forward:/preview/index.html");
		registry.addViewController("/preview/").setViewName("forward:/preview/index.html");
		registry.addViewController("/media").setViewName("forward:/media/index.html");
		registry.addViewController("/media/").setViewName("forward:/media/index.html");
	}

	@Override
	public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
		configurer.setDefaultTimeout(-1);
	}

	/**
	 * Registers a React SPA context path. For any request under the given context,
	 * if the requested resource doesn't exist, falls back to /public/index.html
	 * so React Router can handle the routing client-side.
	 */
	private static void registryReact(ResourceHandlerRegistry registry, String context) {
		registry.addResourceHandler("%s/**".formatted(context))
				.addResourceLocations("classpath:/public/")
				.resourceChain(true)
				.addResolver(new PathResourceResolver() {
					@Override
					protected Resource getResource(@NotNull String resourcePath, @NotNull Resource location)
							throws IOException {
						Resource requestedResource = location.createRelative(resourcePath);
						return requestedResource.exists() && requestedResource.isReadable()
								? requestedResource
								: new ClassPathResource("/public/index.html");
					}
				});
	}
}
