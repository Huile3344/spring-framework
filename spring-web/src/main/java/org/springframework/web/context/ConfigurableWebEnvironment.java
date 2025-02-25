/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.context;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.lang.Nullable;

/**
 * 专业化的 ConfigurableEnvironment 允许在 ServletContext 和（可选）ServletConfig 可用的
 * 最早时刻初始化与 servlet 相关的 PropertySource 对象。
 * <p>Specialization of {@link ConfigurableEnvironment} allowing initialization of
 * servlet-related {@link org.springframework.core.env.PropertySource} objects at the
 * earliest moment that the {@link ServletContext} and (optionally) {@link ServletConfig}
 * become available.
 *
 * @author Chris Beams
 * @since 3.1.2
 * @see ConfigurableWebApplicationContext#getEnvironment()
 */
public interface ConfigurableWebEnvironment extends ConfigurableEnvironment {

	/**
	 * 使用给定的参数将任何充当占位符的存根属性源实例替换为真实的 servlet 上下文/配置属性源。
	 * <p>Replace any {@linkplain
	 * org.springframework.core.env.PropertySource.StubPropertySource stub property source}
	 * instances acting as placeholders with real servlet context/config property sources
	 * using the given parameters.
	 * @param servletContext the {@link ServletContext} (may not be {@code null})
	 * @param servletConfig the {@link ServletConfig} ({@code null} if not available)
	 * @see org.springframework.web.context.support.WebApplicationContextUtils#initServletPropertySources(
	 * org.springframework.core.env.MutablePropertySources, ServletContext, ServletConfig)
	 */
	void initPropertySources(@Nullable ServletContext servletContext, @Nullable ServletConfig servletConfig);

}
