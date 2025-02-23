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

import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Abstract base class representing a source of name/value property pairs. The underlying
 * {@linkplain #getSource() source object} may be of any type {@code T} that encapsulates
 * properties. Examples include {@link java.util.Properties} objects, {@link java.util.Map}
 * objects, {@code ServletContext} and {@code ServletConfig} objects (for access to init
 * parameters). Explore the {@code PropertySource} type hierarchy to see provided
 * implementations.
 *
 * <p>{@code PropertySource} objects are not typically used in isolation, but rather
 * through a {@link PropertySources} object, which aggregates property sources and in
 * conjunction with a {@link PropertyResolver} implementation that can perform
 * precedence-based searches across the set of {@code PropertySources}.
 *
 * <p>{@code PropertySource} identity is determined not based on the content of
 * encapsulated properties, but rather based on the {@link #getName() name} of the
 * {@code PropertySource} alone. This is useful for manipulating {@code PropertySource}
 * objects when in collection contexts. See operations in {@link MutablePropertySources}
 * as well as the {@link #named(String)} and {@link #toString()} methods for details.
 *
 * <p>Note that when working with @{@link
 * org.springframework.context.annotation.Configuration Configuration} classes that
 * the @{@link org.springframework.context.annotation.PropertySource PropertySource}
 * annotation provides a convenient and declarative way of adding property sources to the
 * enclosing {@code Environment}.
 *
 * <p>表示名称/值属性对的源的抽象基类。底层的源对象可以是封装属性的任何类型T 。示例包括 Properties 对象、 Map 对象、
 * ServletContext和ServletConfig对象（用于访问 init 参数）。探索PropertySource类型层次结构以查看提供的实现。
 * <p>PropertySource对象通常不会单独使用，而是通过 PropertySources 对象使用，该对象聚合属性源并与 PropertyResolver
 * 实现结合使用，该实现可以跨PropertySources集执行基于优先级的搜索。
 * <p>PropertySource标识不是根据封装属性的内容来确定的，而是单独根据PropertySource 的 name 来确定的。
 * 这对于操作处于集合上下文中的对象的PropertySource很有用。查看 MutablePropertySources 中的操作以及
 * named(String)和toString()方法以了解详细信息。
 * <p>请注意，在使用 @Configuration 注解的类时，@PropertySource 注释提供了一种方便的声明性方式将属性源添加到封闭 Environment 。
 *
 * @author Chris Beams
 * @since 3.1
 * @param <T> the source type
 * @see PropertySources
 * @see PropertyResolver
 * @see PropertySourcesPropertyResolver
 * @see MutablePropertySources
 * @see org.springframework.context.annotation.PropertySource
 */
public abstract class PropertySource<T> {

	protected final Log logger = LogFactory.getLog(getClass());

	// PropertySource标识是单独根据 PropertySource 的 name 来确定的，通过名称判断两者是否相等
	protected final String name;

	// 源对象，用于获取属性值
	protected final T source;


	/**
	 * Create a new {@code PropertySource} with the given name and source object.
	 * <p>使用给定的名称和源对象创建一个新的 PropertySource。
	 * @param name the associated name
	 * @param source the source object
	 */
	public PropertySource(String name, T source) {
		Assert.hasText(name, "Property source name must contain at least one character");
		Assert.notNull(source, "Property source must not be null");
		this.name = name;
		this.source = source;
	}

	/**
	 * Create a new {@code PropertySource} with the given name and with a new
	 * {@code Object} instance as the underlying source.
	 * <p>Often useful in testing scenarios when creating anonymous implementations
	 * that never query an actual source but rather return hard-coded values.
	 * <p>使用给定的名称创建一个新的 PropertySource，并使用新的 Object 实例作为基础源。
	 * <p>在测试场景中，创建从不查询实际源而是返回硬编码值的匿名实现时，这通常很有用。
	 */
	@SuppressWarnings("unchecked")
	public PropertySource(String name) {
		this(name, (T) new Object());
	}


	/**
	 * Return the name of this {@code PropertySource}.
	 * <p>See the {@linkplain PropertySource class-level Javadoc} for details
	 * on property source identity and names.
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Return the underlying source object for this {@code PropertySource}.
	 */
	public T getSource() {
		return this.source;
	}

	/**
	 * Return whether this {@code PropertySource} contains the given name.
	 * <p>This implementation simply checks for a {@code null} return value
	 * from {@link #getProperty(String)}. Subclasses may wish to implement
	 * a more efficient algorithm if possible.
	 * @param name the property name to find
	 */
	public boolean containsProperty(String name) {
		return (getProperty(name) != null);
	}

	/**
	 * Return the value associated with the given name,
	 * or {@code null} if not found.
	 * <p>返回与给定名称关联的值，如果未找到则返回 null。
	 * @param name the property to find
	 * @see PropertyResolver#getRequiredProperty(String)
	 */
	@Nullable
	public abstract Object getProperty(String name);


	/**
	 * This {@code PropertySource} object is equal to the given object if:
	 * <ul>
	 * <li>they are the same instance
	 * <li>the {@code name} properties for both objects are equal
	 * </ul>
	 * <p>No properties other than {@code name} are evaluated.
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof PropertySource<?> that &&
				ObjectUtils.nullSafeEquals(getName(), that.getName())));
	}

	/**
	 * Return a hash code derived from the {@code name} property
	 * of this {@code PropertySource} object.
	 */
	@Override
	public int hashCode() {
		return Objects.hashCode(getName());
	}

	/**
	 * Produce concise output (type and name) if the current log level does not include
	 * debug. If debug is enabled, produce verbose output including the hash code of the
	 * PropertySource instance and every name/value property pair.
	 * <p>This variable verbosity is useful as a property source such as system properties
	 * or environment variables may contain an arbitrary number of property pairs,
	 * potentially leading to difficulties to read exception and log messages.
	 * @see Log#isDebugEnabled()
	 */
	@Override
	public String toString() {
		if (logger.isDebugEnabled()) {
			return getClass().getSimpleName() + "@" + System.identityHashCode(this) +
					" {name='" + getName() + "', properties=" + getSource() + "}";
		}
		else {
			return getClass().getSimpleName() + " {name='" + getName() + "'}";
		}
	}


	/**
	 * Return a {@code PropertySource} implementation intended for collection
	 * comparison purposes only.
	 * <p>Primarily for internal use, but given a collection of {@code PropertySource}
	 * objects, may be used as follows:
	 * <pre class="code">
	 * List&lt;PropertySource&lt;?&gt;&gt; sources = new ArrayList&lt;&gt;();
	 * sources.add(new MapPropertySource("sourceA", mapA));
	 * sources.add(new MapPropertySource("sourceB", mapB));
	 * assert sources.contains(PropertySource.named("sourceA"));
	 * assert sources.contains(PropertySource.named("sourceB"));
	 * assert !sources.contains(PropertySource.named("sourceC"));</pre>
	 * <p>The returned {@code PropertySource} will throw {@code UnsupportedOperationException}
	 * if any methods other than {@code equals(Object)}, {@code hashCode()}, and {@code toString()}
	 * are called.
	 * <p>返回一个 PropertySource 实现，仅用于集合比较目的。
	 * <p>主要用于内部使用，但给定一个 PropertySource 对象集合，可以按如下方式使用：
	 * <pre class="code">
	 * List&lt;PropertySource&lt;?&gt;&gt; sources = new ArrayList&lt;&gt;();
	 * sources.add(new MapPropertySource("sourceA", mapA));
	 * sources.add(new MapPropertySource("sourceB", mapB));
	 * assert sources.contains(PropertySource.named("sourceA"));
	 * assert sources.contains(PropertySource.named("sourceB"));
	 * assert !sources.contains(PropertySource.named("sourceC"));</pre>
	 * <p>如果调用除 equals(Object)、hashCode() 和 toString() 之外的任何方法，则返回的 PropertySource 将抛出 UnsupportedOperationException。
	 * @param name the name of the comparison {@code PropertySource} to be created
	 * and returned
	 */
	public static PropertySource<?> named(String name) {
		return new ComparisonPropertySource(name);
	}


	/**
	 * {@code PropertySource} to be used as a placeholder in cases where an actual
	 * property source cannot be eagerly initialized at application context
	 * creation time.  For example, a {@code ServletContext}-based property source
	 * must wait until the {@code ServletContext} object is available to its enclosing
	 * {@code ApplicationContext}.  In such cases, a stub should be used to hold the
	 * intended default position/order of the property source, then be replaced
	 * during context refresh.
	 * <p>PropertySource用作占位符，用于在应用程序上下文创建时无法急切初始化实际属性源的情况。
	 * 例如，基于ServletContext的属性源必须等待，直到ServletContext对象可用于其封闭的ApplicationContext。
	 * 在这种情况下，应使用存根来保存属性源的预期默认位置/顺序，然后在上下文刷新期间替换。
	 * @see org.springframework.context.support.AbstractApplicationContext#initPropertySources()
	 * @see org.springframework.web.context.support.StandardServletEnvironment
	 * @see org.springframework.web.context.support.ServletContextPropertySource
	 */
	public static class StubPropertySource extends PropertySource<Object> {

		public StubPropertySource(String name) {
			super(name);
		}

		/**
		 * Always returns {@code null}.
		 */
		@Override
		@Nullable
		public String getProperty(String name) {
			return null;
		}
	}


	/**
	 * A {@code PropertySource} implementation intended for collection comparison
	 * purposes.
	 * <p>用于集合比较目的的 PropertySource 实现。
	 *
	 * @see PropertySource#named(String)
	 */
	static class ComparisonPropertySource extends StubPropertySource {

		private static final String USAGE_ERROR =
				"ComparisonPropertySource instances are for use with collection comparison only";

		public ComparisonPropertySource(String name) {
			super(name);
		}

		@Override
		public Object getSource() {
			throw new UnsupportedOperationException(USAGE_ERROR);
		}

		@Override
		public boolean containsProperty(String name) {
			throw new UnsupportedOperationException(USAGE_ERROR);
		}

		@Override
		@Nullable
		public String getProperty(String name) {
			throw new UnsupportedOperationException(USAGE_ERROR);
		}
	}

}
