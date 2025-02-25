/*
 * Copyright 2002-2023 the original author or authors.
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
 * 代表当前应用程序运行环境的接口。 对应用程序环境的两个关键方面进行建模：配置文件(profiles)和属性(properties)。
 * 与属性访问相关的方法通过 PropertyResolver 超级接口暴露。
 * <p>profile 是一个命名的、逻辑的 bean 定义组，仅当给定的 profile 文件处于活动状态时才会向容器注册。
 * Bean 可以分配给 profile，无论是在 XML 中还是通过注释定义；有关语法详细信息，请参阅 spring-beans 3.1 架构
 * 或@Profile注释。与 profile 相关的Environment对象的作用是确定哪些 profile（如果有）当前处于活动状态，
 * 以及默认情况下哪些 profile（如果有）应该处于活动状态。
 * <p>属性在几乎所有应用程序中都发挥着重要作用，并且可能源自多种来源：属性文件、JVM 系统属性、系统环境变量、
 * JNDI、Servlet 上下文参数、临时属性对象、映射等。 与属性相关的Environment对象的作用就是为用户提供一个方便的服务接口，
 * 用于配置属性源并从中解析属性。
 * <p>在ApplicationContext中管理的Environment Bean 可通过 EnvironmentAware 或@Inject 注册，以便直接查询 profile
 * 文件状态或解析属性。
 * <p>然而，在大多数情况下，应用程序级 bean 不需要直接与Environment 交互，而是可以请求将${...}属性值替换为
 * 属性占位符配置程序，例如 PropertySourcesPlaceholderConfigurer，它本身是EnvironmentAware ，并且在
 * 使用 <context:property-placeholder/> 时默认注册。
 * <p>必须通过 AbstractApplicationContext子类getEnvironment()方法返回的 ConfigurableEnvironment接口
 * 来配置Environment对象。看 ConfigurableEnvironment Javadoc 用于演示在应用程序refresh()之前对属性源进行
 * 操作的使用示例。
 * <p>Interface representing the environment in which the current application is running.
 * Models two key aspects of the application environment: <em>profiles</em> and
 * <em>properties</em>. Methods related to property access are exposed via the
 * {@link PropertyResolver} superinterface.
 *
 * <p>A <em>profile</em> is a named, logical group of bean definitions to be registered
 * with the container only if the given profile is <em>active</em>. Beans may be assigned
 * to a profile whether defined in XML or via annotations; see the spring-beans 3.1 schema
 * or the {@link org.springframework.context.annotation.Profile @Profile} annotation for
 * syntax details. The role of the {@code Environment} object with relation to profiles is
 * in determining which profiles (if any) are currently {@linkplain #getActiveProfiles
 * active}, and which profiles (if any) should be {@linkplain #getDefaultProfiles active
 * by default}.
 *
 * <p><em>Properties</em> play an important role in almost all applications, and may
 * originate from a variety of sources: properties files, JVM system properties, system
 * environment variables, JNDI, servlet context parameters, ad-hoc Properties objects,
 * Maps, and so on. The role of the {@code Environment} object with relation to properties
 * is to provide the user with a convenient service interface for configuring property
 * sources and resolving properties from them.
 *
 * <p>Beans managed within an {@code ApplicationContext} may register to be {@link
 * org.springframework.context.EnvironmentAware EnvironmentAware} or {@code @Inject} the
 * {@code Environment} in order to query profile state or resolve properties directly.
 *
 * <p>In most cases, however, application-level beans should not need to interact with the
 * {@code Environment} directly but instead may request to have {@code ${...}} property
 * values replaced by a property placeholder configurer such as
 * {@link org.springframework.context.support.PropertySourcesPlaceholderConfigurer
 * PropertySourcesPlaceholderConfigurer}, which itself is {@code EnvironmentAware} and
 * registered by default when using {@code <context:property-placeholder/>}.
 *
 * <p>Configuration of the {@code Environment} object must be done through the
 * {@code ConfigurableEnvironment} interface, returned from all
 * {@code AbstractApplicationContext} subclass {@code getEnvironment()} methods. See
 * {@link ConfigurableEnvironment} Javadoc for usage examples demonstrating manipulation
 * of property sources prior to application context {@code refresh()}.
 *
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 3.1
 * @see PropertyResolver
 * @see EnvironmentCapable
 * @see ConfigurableEnvironment
 * @see AbstractEnvironment
 * @see StandardEnvironment
 * @see org.springframework.context.EnvironmentAware
 * @see org.springframework.context.ConfigurableApplicationContext#getEnvironment
 * @see org.springframework.context.ConfigurableApplicationContext#setEnvironment
 * @see org.springframework.context.support.AbstractApplicationContext#createEnvironment
 */
public interface Environment extends PropertyResolver {

	/**
	 * 返回为此环境明确激活的 profile 集。profile 用于创建有条件注册的 bean 定义的逻辑分组，
	 * 例如基于部署环境。可以通过将“spring.profiles.active”设置为系统属性或调用
	 * ConfigurableEnvironment.setActiveProfiles(String...) 来激活 profile。
	 * <p>如果没有明确指定 profile 为活动配置文件，则将自动激活任何默认配置文件。
	 * <p>Return the set of profiles explicitly made active for this environment. Profiles
	 * are used for creating logical groupings of bean definitions to be registered
	 * conditionally, for example based on deployment environment. Profiles can be
	 * activated by setting {@linkplain AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 * "spring.profiles.active"} as a system property or by calling
	 * {@link ConfigurableEnvironment#setActiveProfiles(String...)}.
	 * <p>If no profiles have explicitly been specified as active, then any
	 * {@linkplain #getDefaultProfiles() default profiles} will automatically be activated.
	 * @see #getDefaultProfiles
	 * @see ConfigurableEnvironment#setActiveProfiles
	 * @see AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 */
	String[] getActiveProfiles();

	/**
	 * 当没有明确设置激活 profile 时，返回默认激活的 profile 集。
	 * <p>Return the set of profiles to be active by default when no active profiles have
	 * been set explicitly.
	 * @see #getActiveProfiles
	 * @see ConfigurableEnvironment#setDefaultProfiles
	 * @see AbstractEnvironment#DEFAULT_PROFILES_PROPERTY_NAME
	 */
	String[] getDefaultProfiles();

	/**
	 * 确定给定的 profile 表达式之一是否与激活的 profile 匹配 - 或者在没有明确激活的 profile 的情况下，
	 * 给定的 profile 表达式之一是否与默认 profile 匹配。
	 * <p>profile 表达式允许表达复杂的布尔 profile 逻辑 - 例如“p1 & p2”、“(p1 & p2) | p3”等。
	 * 有关支持的表达式语法的详细信息，请参阅 Profiles.of(String...)。
	 * <p>此方法是 env.acceptsProfiles(Profiles.of(profileExpressions)) 的便捷快捷方式。
	 * <p>Determine whether one of the given profile expressions matches the
	 * {@linkplain #getActiveProfiles() active profiles} &mdash; or in the case
	 * of no explicit active profiles, whether one of the given profile expressions
	 * matches the {@linkplain #getDefaultProfiles() default profiles}.
	 * <p>Profile expressions allow for complex, boolean profile logic to be
	 * expressed &mdash; for example {@code "p1 & p2"}, {@code "(p1 & p2) | p3"},
	 * etc. See {@link Profiles#of(String...)} for details on the supported
	 * expression syntax.
	 * <p>This method is a convenient shortcut for
	 * {@code env.acceptsProfiles(Profiles.of(profileExpressions))}.
	 * @since 5.3.28
	 * @see Profiles#of(String...)
	 * @see #acceptsProfiles(Profiles)
	 */
	default boolean matchesProfiles(String... profileExpressions) {
		return acceptsProfiles(Profiles.of(profileExpressions));
	}

	/**
	 * 确定给定的配置文件中是否有一个或多个处于活动状态 — 或者，在没有明确激活的 profile 的情况下，
	 * 给定的 profile 中是否有一个或多个包含在默认 profile 集中。
	 * <p>如果 profile 以“！”开头，则逻辑将被反转，这意味着如果给定的 profile 不处于活动状态，
	 * 则此方法将返回 true。例如，如果 profile “p1”处于活动状态或“p2”不处于活动状态，
	 * 则 env.acceptsProfiles("p1", "!p2") 将返回 true。
	 * <p>Determine whether one or more of the given profiles is active &mdash; or
	 * in the case of no explicit {@linkplain #getActiveProfiles() active profiles},
	 * whether one or more of the given profiles is included in the set of
	 * {@linkplain #getDefaultProfiles() default profiles}.
	 * <p>If a profile begins with '!' the logic is inverted, meaning this method
	 * will return {@code true} if the given profile is <em>not</em> active. For
	 * example, {@code env.acceptsProfiles("p1", "!p2")} will return {@code true}
	 * if profile 'p1' is active or 'p2' is not active.
	 * @throws IllegalArgumentException if called with a {@code null} array, an
	 * empty array, zero arguments or if any profile is {@code null}, empty, or
	 * whitespace only
	 * @see #getActiveProfiles
	 * @see #getDefaultProfiles
	 * @see #matchesProfiles(String...)
	 * @see #acceptsProfiles(Profiles)
	 * @deprecated as of 5.1 in favor of {@link #acceptsProfiles(Profiles)} or
	 * {@link #matchesProfiles(String...)}
	 */
	@Deprecated
	boolean acceptsProfiles(String... profiles);

	/**
	 * 确定给定的 Profiles 谓词是否与活动 profile 匹配 — 或者在没有明确活动 profile 的情况下，
	 * 给定的 Profiles 谓词是否与默认 profile 匹配。
	 * <p>如果您希望直接以字符串形式提供配置文件表达式，请改用 matchesProfiles(String...)。
	 * <p>Determine whether the given {@link Profiles} predicate matches the
	 * {@linkplain #getActiveProfiles() active profiles} &mdash; or in the case
	 * of no explicit active profiles, whether the given {@code Profiles} predicate
	 * matches the {@linkplain #getDefaultProfiles() default profiles}.
	 * <p>If you wish provide profile expressions directly as strings, use
	 * {@link #matchesProfiles(String...)} instead.
	 * @since 5.1
	 * @see #matchesProfiles(String...)
	 * @see Profiles#of(String...)
	 */
	boolean acceptsProfiles(Profiles profiles);

}
