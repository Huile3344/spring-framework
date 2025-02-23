/*
 * Copyright 2002-2024 the original author or authors.
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

import java.util.Locale;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 设计用于系统环境变量的专业化的 MapPropertySource 。 补偿 Bash 和其他 shell 中不允许变量的约束，
 * 包含句点字符和/或连字符；也允许大 属性名称的变体，以便更惯用的 shell 使用。
 *
 * <p>例如，调用getProperty("foo.bar")将尝试查找一个值，对于原始属性或任何“等效”属性，返回第一个找到的：
 * <ul>
 * <li>foo.bar - 原始名称
 * <li>foo_bar - 用下划线表示句点（如果有）
 * <li>FOO.BAR - 原始，大写
 * <li>FOO_BAR - 带下划线和大写字母</li>
* </ul>
 * 上述任何连字符变体都可以工作，甚至可以混合点/连字符变体。
 *
 * <p>这同样适用于调用containsProperty(String) ，如果存在上述任何属性，它返回则为true ，否则为false 。
 *
 * <p>当将活动或默认 profiles 指定为环境变量。 Bash 下不允许以下行为：
 * <pre class="code">spring.profiles.active=p1 java -classpath ... MyApp</pre>
 *
 * 但是，以下语法是允许的，并且也是更常规的：
 * <pre class="code">SPRING_PROFILES_ACTIVE=p1 java -classpath ... MyApp</pre>
 *
 * <p>为此类（或包）启用消息的调试或跟踪级别日志记录，解释这些“属性名称解析”何时发生。
 *
 * <p>默认情况下，此属性源包含在 StandardEnvironment 及其所有子类中。
 *
 * <p>Specialization of {@link MapPropertySource} designed for use with
 * {@linkplain AbstractEnvironment#getSystemEnvironment() system environment variables}.
 * Compensates for constraints in Bash and other shells that do not allow for variables
 * containing the period character and/or hyphen character; also allows for uppercase
 * variations on property names for more idiomatic shell use.
 *
 * <p>For example, a call to {@code getProperty("foo.bar")} will attempt to find a value
 * for the original property or any 'equivalent' property, returning the first found:
 * <ul>
 * <li>{@code foo.bar} - the original name</li>
 * <li>{@code foo_bar} - with underscores for periods (if any)</li>
 * <li>{@code FOO.BAR} - original, with upper case</li>
 * <li>{@code FOO_BAR} - with underscores and upper case</li>
 * </ul>
 * Any hyphen variant of the above would work as well, or even mix dot/hyphen variants.
 *
 * <p>The same applies for calls to {@link #containsProperty(String)}, which returns
 * {@code true} if any of the above properties are present, otherwise {@code false}.
 *
 * <p>This feature is particularly useful when specifying active or default profiles as
 * environment variables. The following is not allowable under Bash:
 *
 * <pre class="code">spring.profiles.active=p1 java -classpath ... MyApp</pre>
 *
 * However, the following syntax is permitted and is also more conventional:
 *
 * <pre class="code">SPRING_PROFILES_ACTIVE=p1 java -classpath ... MyApp</pre>
 *
 * <p>Enable debug- or trace-level logging for this class (or package) for messages
 * explaining when these 'property name resolutions' occur.
 *
 * <p>This property source is included by default in {@link StandardEnvironment}
 * and all its subclasses.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see StandardEnvironment
 * @see AbstractEnvironment#getSystemEnvironment()
 * @see AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
 */
public class SystemEnvironmentPropertySource extends MapPropertySource {

	/**
	 * Create a new {@code SystemEnvironmentPropertySource} with the given name and
	 * delegating to the given {@code MapPropertySource}.
	 */
	public SystemEnvironmentPropertySource(String name, Map<String, Object> source) {
		super(name, source);
	}


	/**
	 * Return {@code true} if a property with the given name or any underscore/uppercase variant
	 * thereof exists in this property source.
	 */
	@Override
	public boolean containsProperty(String name) {
		return (getProperty(name) != null);
	}

	/**
	 * This implementation returns {@code true} if a property with the given name or
	 * any underscore/uppercase variant thereof exists in this property source.
	 */
	@Override
	@Nullable
	public Object getProperty(String name) {
		String actualName = resolvePropertyName(name);
		if (logger.isDebugEnabled() && !name.equals(actualName)) {
			logger.debug("PropertySource '" + getName() + "' does not contain property '" + name +
					"', but found equivalent '" + actualName + "'");
		}
		return super.getProperty(actualName);
	}

	/**
	 * Check to see if this property source contains a property with the given name, or
	 * any underscore / uppercase variation thereof. Return the resolved name if one is
	 * found or otherwise the original name. Never returns {@code null}.
	 */
	protected final String resolvePropertyName(String name) {
		Assert.notNull(name, "Property name must not be null");
		String resolvedName = checkPropertyName(name);
		if (resolvedName != null) {
			return resolvedName;
		}
		String uppercasedName = name.toUpperCase(Locale.ROOT);
		if (!name.equals(uppercasedName)) {
			resolvedName = checkPropertyName(uppercasedName);
			if (resolvedName != null) {
				return resolvedName;
			}
		}
		return name;
	}

	@Nullable
	private String checkPropertyName(String name) {
		// Check name as-is
		if (this.source.containsKey(name)) {
			return name;
		}
		// Check name with just dots replaced
		String noDotName = name.replace('.', '_');
		if (!name.equals(noDotName) && this.source.containsKey(noDotName)) {
			return noDotName;
		}
		// Check name with just hyphens replaced
		String noHyphenName = name.replace('-', '_');
		if (!name.equals(noHyphenName) && this.source.containsKey(noHyphenName)) {
			return noHyphenName;
		}
		// Check name with dots and hyphens replaced
		String noDotNoHyphenName = noDotName.replace('-', '_');
		if (!noDotName.equals(noDotNoHyphenName) && this.source.containsKey(noDotNoHyphenName)) {
			return noDotNoHyphenName;
		}
		// Give up
		return null;
	}

}
