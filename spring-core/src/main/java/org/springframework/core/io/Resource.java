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

package org.springframework.core.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

import org.springframework.lang.Nullable;
import org.springframework.util.FileCopyUtils;

/**
 * Interface for a resource descriptor that abstracts from the actual
 * type of underlying resource, such as a file or class path resource.
 *
 * <p>An InputStream can be opened for every resource if it exists in
 * physical form, but a URL or File handle can just be returned for
 * certain resources. The actual behavior is implementation-specific.
 *
 * <p>从底层资源的实际类型（例如文件或类路径资源）抽象出来的资源描述符的接口。
 * <p>如果每个资源以物理形式存在，则可以为每个资源打开一个InputStream，但对于某些资源只能返回一个URL 或文件句柄。
 * 实际行为是特定于实现的。
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @since 28.12.2003
 * @see #getInputStream()
 * @see #getURL()
 * @see #getURI()
 * @see #getFile()
 * @see WritableResource
 * @see ContextResource
 * @see UrlResource
 * @see FileUrlResource
 * @see FileSystemResource
 * @see ClassPathResource
 * @see ByteArrayResource
 * @see InputStreamResource
 */
public interface Resource extends InputStreamSource {

	/**
	 * Determine whether this resource actually exists in physical form.
	 * <p>This method performs a definitive existence check, whereas the
	 * existence of a {@code Resource} handle only guarantees a valid
	 * descriptor handle.
	 *
	 * <p>获取此资源是否以物理形式实际存在。
	 * <p>此方法执行明确的存在性检查，而资源句柄的存在仅保证描述符句柄有效。
	 */
	boolean exists();

	/**
	 * Indicate whether non-empty contents of this resource can be read via
	 * {@link #getInputStream()}.
	 * <p>Will be {@code true} for typical resource descriptors that exist
	 * since it strictly implies {@link #exists()} semantics as of 5.1.
	 * Note that actual content reading may still fail when attempted.
	 * However, a value of {@code false} is a definitive indication
	 * that the resource content cannot be read.
	 *
	 * <p>指示是否可以通过 getInputStream() 读取此资源的非空内容。
	 * <p>对于存在的典型资源描述符，该值为 true，因为自 5.1 起它严格暗示了 exist() 语义。
	 * 请注意，尝试读取实际内容时仍可能失败。但是，false 值明确表示无法读取资源内容。
	 * @see #getInputStream()
	 * @see #exists()
	 */
	default boolean isReadable() {
		return exists();
	}

	/**
	 * Indicate whether this resource represents a handle with an open stream.
	 * If {@code true}, the InputStream cannot be read multiple times,
	 * and must be read and closed to avoid resource leaks.
	 * <p>Will be {@code false} for typical resource descriptors.
	 *
	 * <p>指示此资源是否代表具有开放流的句柄。如果为 true，则无法多次读取 InputStream，必须读取并关闭以避免资源泄漏。
	 * <p>对于典型的资源描述符，将为 false。
	 */
	default boolean isOpen() {
		return false;
	}

	/**
	 * Determine whether this resource represents a file in a file system.
	 * <p>A value of {@code true} strongly suggests (but does not guarantee)
	 * that a {@link #getFile()} call will succeed.
	 * <p>This is conservatively {@code false} by default.
	 *
	 * <p>获取此资源是否代表文件系统中的文件。
	 * <p>true 值强烈暗示（但不保证） getFile() 调用将成功。
	 * <p>默认情况下，保守地为 false。
	 * @since 5.0
	 * @see #getFile()
	 */
	default boolean isFile() {
		return false;
	}

	/**
	 * Return a URL handle for this resource.
	 *
	 * <p>返回此资源的 URL 句柄。
	 * @throws IOException if the resource cannot be resolved as URL,
	 * i.e. if the resource is not available as a descriptor
	 */
	URL getURL() throws IOException;

	/**
	 * Return a URI handle for this resource.
	 *
	 * <p>返回此资源的 URI 句柄。
	 * @throws IOException if the resource cannot be resolved as URI,
	 * i.e. if the resource is not available as a descriptor
	 * @since 2.5
	 */
	URI getURI() throws IOException;

	/**
	 * Return a File handle for this resource.
	 *
	 * <p>返回此资源的文件句柄。
	 * @throws java.io.FileNotFoundException if the resource cannot be resolved as
	 * absolute file path, i.e. if the resource is not available in a file system
	 * @throws IOException in case of general resolution/reading failures
	 * @see #getInputStream()
	 */
	File getFile() throws IOException;

	/**
	 * Return a {@link ReadableByteChannel}.
	 * <p>It is expected that each call creates a <i>fresh</i> channel.
	 * <p>The default implementation returns {@link Channels#newChannel(InputStream)}
	 * with the result of {@link #getInputStream()}.
	 *
	 * <p>返回一个 ReadableByteChannel。
	 * <p>预计每次调用都会创建一个新的通道。
	 * <p>默认实现返回 Channels.newChannel(InputStream) 和 getInputStream() 的结果。
	 * @return the byte channel for the underlying resource (must not be {@code null})
	 * @throws java.io.FileNotFoundException if the underlying resource doesn't exist
	 * @throws IOException if the content channel could not be opened
	 * @since 5.0
	 * @see #getInputStream()
	 */
	default ReadableByteChannel readableChannel() throws IOException {
		return Channels.newChannel(getInputStream());
	}

	/**
	 * Return the contents of this resource as a byte array.
	 * <p>以字节数组的形式返回此资源的内容。
	 * @return the contents of this resource as byte array
	 * @throws java.io.FileNotFoundException if the resource cannot be resolved as
	 * absolute file path, i.e. if the resource is not available in a file system
	 * @throws IOException in case of general resolution/reading failures
	 * @since 6.0.5
	 */
	default byte[] getContentAsByteArray() throws IOException {
		return FileCopyUtils.copyToByteArray(getInputStream());
	}

	/**
	 * Return the contents of this resource as a string, using the specified charset.
	 * <p>使用指定的字符集以字符串形式返回此资源的内容
	 * @param charset the charset to use for decoding
	 * @return the contents of this resource as a {@code String}
	 * @throws java.io.FileNotFoundException if the resource cannot be resolved as
	 * absolute file path, i.e. if the resource is not available in a file system
	 * @throws IOException in case of general resolution/reading failures
	 * @since 6.0.5
	 */
	default String getContentAsString(Charset charset) throws IOException {
		return FileCopyUtils.copyToString(new InputStreamReader(getInputStream(), charset));
	}

	/**
	 * Determine the content length for this resource.
	 * <p>获取此资源的内容长度。
	 * @throws IOException if the resource cannot be resolved
	 * (in the file system or as some other known physical resource type)
	 */
	long contentLength() throws IOException;

	/**
	 * Determine the last-modified timestamp for this resource.
	 * <p>获取此资源的最后修改时间戳。
	 * @throws IOException if the resource cannot be resolved
	 * (in the file system or as some other known physical resource type)
	 */
	long lastModified() throws IOException;

	/**
	 * Create a resource relative to this resource.
	 * <p>创建与此资源相对路径的资源。
	 * @param relativePath the relative path (relative to this resource)
	 * @return the resource handle for the relative resource
	 * @throws IOException if the relative resource cannot be determined
	 */
	Resource createRelative(String relativePath) throws IOException;

	/**
	 * Determine the filename for this resource &mdash; typically the last
	 * part of the path &mdash; for example, {@code "myfile.txt"}.
	 * <p>Returns {@code null} if this type of resource does not
	 * have a filename.
	 * <p>Implementations are encouraged to return the filename unencoded.
	 *
	 * <p>获取此资源的文件名 — 通常是路径的最后一部分 — 例如“myfile.txt”。
	 * <p>如果此类资源没有文件名，则返回 null。
	 * <p>鼓励实现返回未编码的文件名。
	 */
	@Nullable
	String getFilename();

	/**
	 * Return a description for this resource,
	 * to be used for error output when working with the resource.
	 * <p>Implementations are also encouraged to return this value
	 * from their {@code toString} method.
	 *
	 * <p>返回此资源的描述，用于处理资源时的错误输出。
	 * <p>还鼓励实现从其 toString 方法返回此值。
	 * @see Object#toString()
	 */
	String getDescription();

}
