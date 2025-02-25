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

import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.lang.Nullable;

/**
 * 大多数（不是全部） PropertyResolver 类型都会实现配置接口。提供访问和定制 ConversionService 的基础设施，
 * ConversionService 用于将属性值从一种类型转换为另一种类型。
 * <p>Configuration interface to be implemented by most if not all {@link PropertyResolver}
 * types. Provides facilities for accessing and customizing the
 * {@link org.springframework.core.convert.ConversionService ConversionService}
 * used when converting property values from one type to another.
 *
 * @author Chris Beams
 * @author Stephane Nicoll
 * @since 3.1
 */
public interface ConfigurablePropertyResolver extends PropertyResolver {

	/**
	 * 返回在对属性执行类型转换时使用的 ConfigurableConversionService。
	 * 返回的转换服务的可配置性允许方便地添加和删除单个 Converter 实例：
	 * <pre class="code">
	 * ConfigurableConversionService cs = env.getConversionService();
	 * cs.addConverter(new FooConverter());
	 * </pre>
	 *
	 * <p>Return the {@link ConfigurableConversionService} used when performing type
	 * conversions on properties.
	 * <p>The configurable nature of the returned conversion service allows for
	 * the convenient addition and removal of individual {@code Converter} instances:
	 * <pre class="code">
	 * ConfigurableConversionService cs = env.getConversionService();
	 * cs.addConverter(new FooConverter());
	 * </pre>
	 * @see PropertyResolver#getProperty(String, Class)
	 * @see org.springframework.core.convert.converter.ConverterRegistry#addConverter
	 */
	ConfigurableConversionService getConversionService();

	/**
	 * 设置在对属性执行类型转换时要使用的 ConfigurableConversionService。
	 * <p>注意：作为完全替换 ConversionService 的替代方法，请考虑通过深入研究 getConversionService()
	 * 并调用 #addConverter 等方法来添加或删除单个 Converter 实例。
	 * <p>Set the {@link ConfigurableConversionService} to be used when performing type
	 * conversions on properties.
	 * <p><strong>Note:</strong> as an alternative to fully replacing the
	 * {@code ConversionService}, consider adding or removing individual
	 * {@code Converter} instances by drilling into {@link #getConversionService()}
	 * and calling methods such as {@code #addConverter}.
	 * @see PropertyResolver#getProperty(String, Class)
	 * @see #getConversionService()
	 * @see org.springframework.core.convert.converter.ConverterRegistry#addConverter
	 */
	void setConversionService(ConfigurableConversionService conversionService);

	/**
	 * 设置该解析器替换的占位符必须以其开头的前缀。
	 * <p>Set the prefix that placeholders replaced by this resolver must begin with.
	 */
	void setPlaceholderPrefix(String placeholderPrefix);

	/**
	 * 设置该解析器替换的占位符必须以此后缀结尾。
	 * <p>Set the suffix that placeholders replaced by this resolver must end with.
	 */
	void setPlaceholderSuffix(String placeholderSuffix);

	/**
	 * 指定此解析器替换的占位符与其关联的默认值之间的分隔字符，如果不应将此类特殊字符处理为值分隔符，则为 null。
	 * <p>Specify the separating character between the placeholders replaced by this
	 * resolver and their associated default value, or {@code null} if no such
	 * special character should be processed as a value separator.
	 */
	void setValueSeparator(@Nullable String valueSeparator);

	/**
	 * 指定用于忽略占位符前缀或值分隔符的转义字符，如果不应进行转义，则为 null。
	 * <p>Specify the escape character to use to ignore placeholder prefix or
	 * value separator, or {@code null} if no escaping should take place.
	 * @since 6.2
	 */
	void setEscapeCharacter(@Nullable Character escapeCharacter);

	/**
	 * 设置在给定属性的值中嵌套无法解析的占位符时是否抛出异常。
	 * false 值表示严格解析，即会抛出异常。
	 * true 值表示无法解析的嵌套占位符应以未解析的 ${...} 形式传递。
	 * <p>getProperty(String) 及其变体的实现必须检查此处设置的值，以确定当属性值包含无法解析的占位符时正确的行为。
	 * <p>Set whether to throw an exception when encountering an unresolvable placeholder
	 * nested within the value of a given property. A {@code false} value indicates strict
	 * resolution, i.e. that an exception will be thrown. A {@code true} value indicates
	 * that unresolvable nested placeholders should be passed through in their unresolved
	 * ${...} form.
	 * <p>Implementations of {@link #getProperty(String)} and its variants must inspect
	 * the value set here to determine correct behavior when property values contain
	 * unresolvable placeholders.
	 * @since 3.2
	 */
	void setIgnoreUnresolvableNestedPlaceholders(boolean ignoreUnresolvableNestedPlaceholders);

	/**
	 * 指定必须存在的属性，以通过validateRequiredProperties（）进行验证。
	 * <p>Specify which properties must be present, to be verified by
	 * {@link #validateRequiredProperties()}.
	 */
	void setRequiredProperties(String... requiredProperties);

	/**
	 * 验证 setRequiredProperties 指定的每个属性是否存在且解析为非空值。
	 * <p>Validate that each of the properties specified by
	 * {@link #setRequiredProperties} is present and resolves to a
	 * non-{@code null} value.
	 * @throws MissingRequiredPropertiesException if any of the required
	 * properties are not resolvable.
	 */
	void validateRequiredProperties() throws MissingRequiredPropertiesException;

}
