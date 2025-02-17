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

import java.io.IOException;
import java.io.InputStream;

/**
 * Simple interface for objects that are sources for an {@link InputStream}.
 *
 * <p>This is the base interface for Spring's more extensive {@link Resource} interface.
 *
 * <p>For single-use streams, {@link InputStreamResource} can be used for any
 * given {@code InputStream}. Spring's {@link ByteArrayResource} or any
 * file-based {@code Resource} implementation can be used as a concrete
 * instance, allowing one to read the underlying content stream multiple times.
 * This makes this interface useful as an abstract content source for mail
 * attachments, for example.
 *
 * <p>InputStream源对象的简单接口。
 * <p>这是 Spring 更广泛的 Resource 接口的基础接口。
 * <p>对于一次性流， InputStreamResource 可用于任何给定的InputStream 。
 * Spring 的 ByteArrayResource 或任何基于文件的Resource实现都可以用作具体实例，允许多次读取底层内容流。
 * 例如，这使得该接口可用作邮件附件的抽象内容源。
 *
 * @author Juergen Hoeller
 * @since 20.01.2004
 * @see java.io.InputStream
 * @see Resource
 * @see InputStreamResource
 * @see ByteArrayResource
 */
@FunctionalInterface
public interface InputStreamSource {

	/**
	 * Return an {@link InputStream} for the content of an underlying resource.
	 * <p>It is usually expected that every such call creates a <i>fresh</i> stream.
	 * <p>This requirement is particularly important when you consider an API such
	 * as JavaMail, which needs to be able to read the stream multiple times when
	 * creating mail attachments. For such a use case, it is <i>required</i>
	 * that each {@code getInputStream()} call returns a fresh stream.
	 *
	 * <p>返回底层资源内容的 InputStream。
	 * <p>通常预期每次此类调用都会创建一个新的流。
	 * <p>当您考虑 JavaMail 等 API 时，此要求尤为重要，因为该 API 在创建邮件附件时需要能够多次读取流。对于此类用例，要求每次 getInputStream() 调用都返回一个新的流。
	 * @return the input stream for the underlying resource (must not be {@code null})
	 * @throws java.io.FileNotFoundException if the underlying resource does not exist
	 * @throws IOException if the content stream could not be opened
	 * @see Resource#isReadable()
	 * @see Resource#isOpen()
	 */
	InputStream getInputStream() throws IOException;

}
