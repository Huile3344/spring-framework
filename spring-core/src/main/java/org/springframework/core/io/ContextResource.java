/*
 * Copyright 2002-2007 the original author or authors.
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

package org.springframework.core.io;

/**
 * Extended interface for a resource that is loaded from an enclosing
 * 'context', for example, from a {@link jakarta.servlet.ServletContext} but also
 * from plain classpath paths or relative file system paths (specified
 * without an explicit prefix, hence applying relative to the local
 * {@link ResourceLoader}'s context).
 *
 * <p>从封闭的“上下文”加载资源的扩展接口，例如从 ServletContext 加载，但也能来自普通类路径路径或
 * 相对文件系统路径（指定没有显式前缀，因此相对于本地应用 ResourceLoader 的上下文）。
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see org.springframework.web.context.support.ServletContextResource
 */
public interface ContextResource extends Resource {

	/**
	 * Return the path within the enclosing 'context'.
	 * <p>This is typically path relative to a context-specific root directory,
	 * for example, a ServletContext root or a PortletContext root.
	 *
	 * <p>返回封闭“上下文”内的路径。
	 * <p>这通常是相对于特定于上下文的根目录的路径，例如 ServletContext 根或 PortletContext 根。
	 */
	String getPathWithinContext();

}
