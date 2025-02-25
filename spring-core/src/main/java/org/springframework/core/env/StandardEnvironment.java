/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.core.env;

/**
 * 适合在“标准”（即非web）应用程序中使用的 Environment 实现 。
 *
 * <p>除了 ConfigurableEnvironment 的常用功能之外，例如属性解析和 profile 相关的操作，这个实现类配置了
 * 两个默认属性源，按以下顺序搜索：
 * <ul>
 * <li>system properties
 * <li>system environment variables
 *</ul>
 * <p>也就是说，如果键“xyz”存在于 JVM 系统属性中，也存在于当前进程的环境变量集中，键“xyz”的值将从系统属性
 * environment.getProperty("xyz")调用中返回 。 默认情况下会选择此顺序，因为系统属性是针对每个 JVM 的，
 * 而给定系统上的许多 JVM 中的环境变量可能是相同的。赋予系统属性优先权允许在每个JVM的基础上重写环境变量。
 * <p>这些默认属性源可以被删除、重新排序或替换；并且可以使用 AbstractEnvironment.getPropertySources()
 * 中提供的 MutablePropertySources 实例添加其他属性源。有关使用示例，请参阅 ConfigurableEnvironment
 * Javadoc 的使用示例。
 * <p>参阅 SystemEnvironmentPropertySource javadoc 了解有关 shell 环境（例如 Bash）中属性名称特殊
 * 处理的详细信息，这些环境不允许在变量名称中使用句点字符。
 *
 * <p>{@link Environment} implementation suitable for use in 'standard' (i.e. non-web)
 * applications.
 *
 * <p>In addition to the usual functions of a {@link ConfigurableEnvironment} such as
 * property resolution and profile-related operations, this implementation configures two
 * default property sources, to be searched in the following order:
 * <ul>
 * <li>{@linkplain AbstractEnvironment#getSystemProperties() system properties}
 * <li>{@linkplain AbstractEnvironment#getSystemEnvironment() system environment variables}
 * </ul>
 *
 * That is, if the key "xyz" is present both in the JVM system properties as well as in
 * the set of environment variables for the current process, the value of key "xyz" from
 * system properties will return from a call to {@code environment.getProperty("xyz")}.
 * This ordering is chosen by default because system properties are per-JVM, while
 * environment variables may be the same across many JVMs on a given system.  Giving
 * system properties precedence allows for overriding of environment variables on a
 * per-JVM basis.
 *
 * <p>These default property sources may be removed, reordered, or replaced; and
 * additional property sources may be added using the {@link MutablePropertySources}
 * instance available from {@link #getPropertySources()}. See
 * {@link ConfigurableEnvironment} Javadoc for usage examples.
 *
 * <p>See {@link SystemEnvironmentPropertySource} javadoc for details on special handling
 * of property names in shell environments (for example, Bash) that disallow period characters in
 * variable names.
 *
 * @author Chris Beams
 * @author Phillip Webb
 * @since 3.1
 * @see ConfigurableEnvironment
 * @see SystemEnvironmentPropertySource
 * @see org.springframework.web.context.support.StandardServletEnvironment
 */
public class StandardEnvironment extends AbstractEnvironment {

	/** System environment property source name: {@value}. */
	public static final String SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME = "systemEnvironment";

	/** JVM system properties property source name: {@value}. */
	public static final String SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME = "systemProperties";


	/**
	 * Create a new {@code StandardEnvironment} instance with a default
	 * {@link MutablePropertySources} instance.
	 */
	public StandardEnvironment() {
	}

	/**
	 * Create a new {@code StandardEnvironment} instance with a specific
	 * {@link MutablePropertySources} instance.
	 * @param propertySources property sources to use
	 * @since 5.3.4
	 */
	protected StandardEnvironment(MutablePropertySources propertySources) {
		super(propertySources);
	}


	/**
	 * 使用适合任何标准 Java 环境的属性源自定义属性源集：
	 * <ul>
	 * <li>“systemProperties”
	 * <li>“systemEnvironment”
	 * </ul>
	 * <p>“systemProperties”中的属性将优先于“systemEnvironment”中的属性。
	 * <p>Customize the set of property sources with those appropriate for any standard
	 * Java environment:
	 * <ul>
	 * <li>{@value #SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME}
	 * <li>{@value #SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME}
	 * </ul>
	 * <p>Properties present in {@value #SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME} will
	 * take precedence over those in {@value #SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME}.
	 * @see AbstractEnvironment#customizePropertySources(MutablePropertySources)
	 * @see #getSystemProperties()
	 * @see #getSystemEnvironment()
	 */
	@Override
	protected void customizePropertySources(MutablePropertySources propertySources) {
		propertySources.addLast(
				new PropertiesPropertySource(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, getSystemProperties()));
		propertySources.addLast(
				new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, getSystemEnvironment()));
	}

}
