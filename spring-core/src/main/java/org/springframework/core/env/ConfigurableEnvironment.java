/*
 * Copyright 2002-2022 the original author or authors.
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

import java.util.Map;

/**
 * 配置接口将由大多数（如果不是全部） Environment 类型实现。 提供用于设置活动和默认 profiles 以及操作底层的设施的
 * 属性源。允许客户设置和验证所需的属性，自定义转换服务，以及通过 ConfigurablePropertyResolver 超级接口的更多能力。
 * <h2>操纵属性源</h2>
 * <p>属性源可能会被删除、重新排序或替换；可以使用 从getPropertySources()返回的 MutablePropertySources 实例添加
 * 其他属性源。以下示例针对StandardEnvironment 的实现 ConfigurableEnvironment ，但通常适用于任何实现， 尽管特定
 * 的默认属性来源可能有所不同。
 * <h4>示例：添加具有最高搜索优先级的新属性源</h4>
 * <pre class="code">
 * ConfigurableEnvironment environment = new StandardEnvironment();
 * MutablePropertySources propertySources = environment.getPropertySources();
 * Map&lt;String, Object&gt; myMap = new HashMap&lt;&gt;();
 * myMap.put("xyz", "myValue");
 * propertySources.addFirst(new MapPropertySource("MY_MAP", myMap));
 * </pre>
 *
 * <h4>示例：删除默认系统属性属性源</h4>
 * <pre class="code">
 * MutablePropertySources propertySources = environment.getPropertySources();
 * propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
 * </pre>
 *
 * <h4>示例：出于测试目的模拟系统环境</h4>
 * <pre class="code">
 * MutablePropertySources propertySources = environment.getPropertySources();
 * MockPropertySource mockEnvVars = new MockPropertySource().withProperty("xyz", "myValue");
 * propertySources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, mockEnvVars);
 * </pre>
 *
 * <p>当ApplicationContext使用 Environment 时，在调用上下文的 refresh() 之前执行任何此类 PropertySource 操作很重要。
 * 这确保了在容器引导过程中所有属性源都可用，包括属性占位符配置者的使用。
 *
 * <p>Configuration interface to be implemented by most if not all {@link Environment} types.
 * Provides facilities for setting active and default profiles and manipulating underlying
 * property sources. Allows clients to set and validate required properties, customize the
 * conversion service and more through the {@link ConfigurablePropertyResolver}
 * superinterface.
 *
 * <h2>Manipulating property sources</h2>
 * <p>Property sources may be removed, reordered, or replaced; and additional
 * property sources may be added using the {@link MutablePropertySources}
 * instance returned from {@link #getPropertySources()}. The following examples
 * are against the {@link StandardEnvironment} implementation of
 * {@code ConfigurableEnvironment}, but are generally applicable to any implementation,
 * though particular default property sources may differ.
 *
 * <h4>Example: adding a new property source with highest search priority</h4>
 * <pre class="code">
 * ConfigurableEnvironment environment = new StandardEnvironment();
 * MutablePropertySources propertySources = environment.getPropertySources();
 * Map&lt;String, Object&gt; myMap = new HashMap&lt;&gt;();
 * myMap.put("xyz", "myValue");
 * propertySources.addFirst(new MapPropertySource("MY_MAP", myMap));
 * </pre>
 *
 * <h4>Example: removing the default system properties property source</h4>
 * <pre class="code">
 * MutablePropertySources propertySources = environment.getPropertySources();
 * propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
 * </pre>
 *
 * <h4>Example: mocking the system environment for testing purposes</h4>
 * <pre class="code">
 * MutablePropertySources propertySources = environment.getPropertySources();
 * MockPropertySource mockEnvVars = new MockPropertySource().withProperty("xyz", "myValue");
 * propertySources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, mockEnvVars);
 * </pre>
 *
 * When an {@link Environment} is being used by an {@code ApplicationContext}, it is
 * important that any such {@code PropertySource} manipulations be performed
 * <em>before</em> the context's {@link
 * org.springframework.context.support.AbstractApplicationContext#refresh() refresh()}
 * method is called. This ensures that all property sources are available during the
 * container bootstrap process, including use by {@linkplain
 * org.springframework.context.support.PropertySourcesPlaceholderConfigurer property
 * placeholder configurers}.
 *
 * @author Chris Beams
 * @since 3.1
 * @see StandardEnvironment
 * @see org.springframework.context.ConfigurableApplicationContext#getEnvironment
 */
public interface ConfigurableEnvironment extends Environment, ConfigurablePropertyResolver {

	/**
	 * 指定此环境的活动配置文件集。在容器引导期间评估配置文件以确定是否应向容器注册 bean 定义。
	 * 任何现有的活动配置文件都将被给定的参数替换；使用零参数调用可清除当前的活动配置文件集。
	 * 使用 addActiveProfile 添加配置文件，同时保留现有配置文件集。
	 * <p>Specify the set of profiles active for this {@code Environment}. Profiles are
	 * evaluated during container bootstrap to determine whether bean definitions
	 * should be registered with the container.
	 * <p>Any existing active profiles will be replaced with the given arguments; call
	 * with zero arguments to clear the current set of active profiles. Use
	 * {@link #addActiveProfile} to add a profile while preserving the existing set.
	 * @throws IllegalArgumentException if any profile is null, empty or whitespace-only
	 * @see #addActiveProfile
	 * @see #setDefaultProfiles
	 * @see org.springframework.context.annotation.Profile
	 * @see AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 */
	void setActiveProfiles(String... profiles);

	/**
	 * 将配置文件添加到当前活动配置文件集中。
	 * <p>Add a profile to the current set of active profiles.
	 * @throws IllegalArgumentException if the profile is null, empty or whitespace-only
	 * @see #setActiveProfiles
	 */
	void addActiveProfile(String profile);

	/**
	 * 如果没有通过 setActiveProfiles 明确激活其他配置文件，则指定默认激活的配置文件集。
	 * <p>Specify the set of profiles to be made active by default if no other profiles
	 * are explicitly made active through {@link #setActiveProfiles}.
	 * @throws IllegalArgumentException if any profile is null, empty or whitespace-only
	 * @see AbstractEnvironment#DEFAULT_PROFILES_PROPERTY_NAME
	 */
	void setDefaultProfiles(String... profiles);

	/**
	 * 以可变形式返回此环境的 PropertySources，允许操作在针对此环境对象解析属性时应搜索的 PropertySource 对象集。
	 * 各种 MutablePropertySources 方法（例如 addFirst、addLast、addBefore 和 addAfter）允许对属性源排序进行
	 * 细粒度控制。这很有用，例如，可确保某些用户定义的属性源的搜索优先级高于默认属性源（例如系统属性集或系统环境变量集）。
	 * <p>Return the {@link PropertySources} for this {@code Environment} in mutable form,
	 * allowing for manipulation of the set of {@link PropertySource} objects that should
	 * be searched when resolving properties against this {@code Environment} object.
	 * The various {@link MutablePropertySources} methods such as
	 * {@link MutablePropertySources#addFirst addFirst},
	 * {@link MutablePropertySources#addLast addLast},
	 * {@link MutablePropertySources#addBefore addBefore} and
	 * {@link MutablePropertySources#addAfter addAfter} allow for fine-grained control
	 * over property source ordering. This is useful, for example, in ensuring that
	 * certain user-defined property sources have search precedence over default property
	 * sources such as the set of system properties or the set of system environment
	 * variables.
	 * @see AbstractEnvironment#customizePropertySources
	 */
	MutablePropertySources getPropertySources();

	/**
	 * 返回 System.getProperties() 的值。
	 * <p>请注意，大多数环境实现都会将此系统属性映射作为要搜索的默认 PropertySource。因此，建议不要直接使用此方法，除非明确打算绕过其他属性源。
	 * <p>Return the value of {@link System#getProperties()}.
	 * <p>Note that most {@code Environment} implementations will include this system
	 * properties map as a default {@link PropertySource} to be searched. Therefore, it is
	 * recommended that this method not be used directly unless bypassing other property
	 * sources is expressly intended.
	 */
	Map<String, Object> getSystemProperties();

	/**
	 * 返回 System.getenv() 的值。
	 * <p>请注意，大多数 Environment 实现都会将此系统环境映射作为要搜索的默认 PropertySource。因此，
	 * 建议不要直接使用此方法，除非明确打算绕过其他属性源。
	 * <p>Return the value of {@link System#getenv()}.
	 * <p>Note that most {@link Environment} implementations will include this system
	 * environment map as a default {@link PropertySource} to be searched. Therefore, it
	 * is recommended that this method not be used directly unless bypassing other
	 * property sources is expressly intended.
	 */
	Map<String, Object> getSystemEnvironment();

	/**
	 * 将给定父环境的活动配置文件、默认配置文件和属性源附加到此（子）环境各自的集合中。
	 * <p>对于父环境和子环境中存在的任何同名 PropertySource 实例，应保留子实例并丢弃父实例。
	 * 这允许子环境覆盖属性源，并避免通过常见属性源类型（例如系统环境和系统属性）进行冗余搜索。
	 * <p>活动和默认配置文件名称也会被过滤掉重复项，以避免混淆和冗余存储。
	 * <p>父环境在任何情况下都保持不变。请注意，在调用合并后对父环境发生的任何更改都不会反映在子环境中。
	 * 因此，在调用合并之前，应注意配置父属性源和配置文件信息。
	 * <p>Append the given parent environment's active profiles, default profiles and
	 * property sources to this (child) environment's respective collections of each.
	 * <p>For any identically-named {@code PropertySource} instance existing in both
	 * parent and child, the child instance is to be preserved and the parent instance
	 * discarded. This has the effect of allowing overriding of property sources by the
	 * child as well as avoiding redundant searches through common property source types,
	 * for example, system environment and system properties.
	 * <p>Active and default profile names are also filtered for duplicates, to avoid
	 * confusion and redundant storage.
	 * <p>The parent environment remains unmodified in any case. Note that any changes to
	 * the parent environment occurring after the call to {@code merge} will not be
	 * reflected in the child. Therefore, care should be taken to configure parent
	 * property sources and profile information prior to calling {@code merge}.
	 * @param parent the environment to merge with
	 * @since 3.1.2
	 * @see org.springframework.context.support.AbstractApplicationContext#setParent
	 */
	void merge(ConfigurableEnvironment parent);

}
