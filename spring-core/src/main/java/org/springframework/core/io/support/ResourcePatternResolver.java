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

package org.springframework.core.io.support;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Strategy interface for resolving a location pattern (for example,
 * an Ant-style path pattern) into {@link Resource} objects.
 *
 * <p>This is an extension to the {@link org.springframework.core.io.ResourceLoader}
 * interface. A passed-in {@code ResourceLoader} (for example, an
 * {@link org.springframework.context.ApplicationContext} passed in via
 * {@link org.springframework.context.ResourceLoaderAware} when running in a context)
 * can be checked whether it implements this extended interface too.
 *
 * <p>{@link PathMatchingResourcePatternResolver} is a standalone implementation
 * that is usable outside an {@code ApplicationContext}, also used by
 * {@link ResourceArrayPropertyEditor} for populating {@code Resource} array bean
 * properties.
 *
 * <p>Can be used with any sort of location pattern &mdash; for example,
 * {@code "/WEB-INF/*-context.xml"}. However, input patterns have to match the
 * strategy implementation. This interface just specifies the conversion method
 * rather than a specific pattern format.
 *
 * <p>This interface also defines a {@value #CLASSPATH_ALL_URL_PREFIX} resource
 * prefix for all matching resources from the module path and the class path. Note
 * that the resource location may also contain placeholders &mdash; for example
 * {@code "/beans-*.xml"}. JAR files or different directories in the module path
 * or class path can contain multiple files of the same name.
 *
 * <p>用于解析资源位置模式（例如， Ant 风格的路径模式）转换为 Resource 对象的策略接口。
 * <p>这是对 ResourceLoader 接口的扩展。可以检查传入的 ResourceLoader（例如，在上下文中
 * 运行时通过 ResourceLoaderAware 传入的 ApplicationContext）是否也实现了此扩展接口。
 * <p>PathMatchingResourcePatternResolver 是一个独立的实现，可以在 ApplicationContext 之外使用，
 * 也可以由用于填充Resource数组 bean 的ResourceArrayPropertyEditor 特性。
 * <p>可与任何类型的位置模式一起使用 - 例如， "/WEB-INF/*-context.xml" 。然而，输入模式必须匹配策略实现。
 * 该接口只是指定了转换方法而不是特定的模式格式。
 * <p>该接口还定义了“classpath*:”资源前缀，用于所有匹配资源的模块路径和类路径。注意：资源位置还可能包含占位符
 * - 例如 "/beans-*.xml" 。 JAR 文件或模块路径或类路径中的不同目录可以包含多个同名文件。
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 1.0.2
 * @see org.springframework.core.io.Resource
 * @see org.springframework.core.io.ResourceLoader
 * @see org.springframework.context.ApplicationContext
 * @see org.springframework.context.ResourceLoaderAware
 */
	public interface ResourcePatternResolver extends ResourceLoader {

	/**
	 * Pseudo URL prefix for all matching resources from the class path: {@code "classpath*:"}.
	 * <p>This differs from ResourceLoader's {@code "classpath:"} URL prefix in
	 * that it retrieves all matching resources for a given path &mdash; for
	 * example, to locate all "beans.xml" files in the root of all deployed JAR
	 * files you can use the location pattern {@code "classpath*:/beans.xml"}.
	 * <p>As of Spring Framework 6.0, the semantics for the {@code "classpath*:"}
	 * prefix have been expanded to include the module path as well as the class path.
	 * <p>来自类路径的所有匹配资源的伪 URL 前缀：“classpath*:”。
	 * <p>这与 ResourceLoader 的“classpath:” URL 前缀不同，因为它检索给定路径的所有匹配资源
	 * — 例如，要在所有部署的 JAR 文件的根目录中找到所有“beans.xml”文件，您可以使用位置模式“classpath*:/beans.xml”。
	 * <p>从 Spring Framework 6.0 开始，“classpath*:”前缀的语义已扩展为包括模块路径和类路径。
	 * @see org.springframework.core.io.ResourceLoader#CLASSPATH_URL_PREFIX
	 */
	String CLASSPATH_ALL_URL_PREFIX = "classpath*:";

	/**
	 * Resolve the given location pattern into {@code Resource} objects.
	 * <p>Overlapping resource entries that point to the same physical
	 * resource should be avoided, as far as possible. The result should
	 * have set semantics.
	 *
	 * <p>将给定的资源位置模式解析为 Resource 对象数组。
	 * <p>应尽可能避免指向同一物理资源的重叠资源条目。结果应具有设置语义。
	 *
	 * @param locationPattern the location pattern to resolve
	 * @return the corresponding {@code Resource} objects
	 * @throws IOException in case of I/O errors
	 */
	Resource[] getResources(String locationPattern) throws IOException;

}
