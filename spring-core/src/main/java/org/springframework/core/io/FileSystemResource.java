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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * {@link Resource} implementation for {@code java.io.File} and
 * {@code java.nio.file.Path} handles with a file system target.
 * Supports resolution as a {@code File} and also as a {@code URL}.
 * Implements the extended {@link WritableResource} interface.
 *
 * <p>Note: This {@link Resource} implementation uses NIO.2 API for read/write
 * interactions and may be constructed with a {@link java.nio.file.Path} handle
 * in which case it will perform all file system interactions via NIO.2, only
 * resorting to {@link File} on {@link #getFile()}.
 *
 * <p>java.io.File和 java.nio.file.Path句柄的文件系统目标的 Resource 实现。 支持解析为File和URL 。
 * 实现扩展的 WritableResource 接口。
 * <p>注意：此 Resource 实现使用 NIO.2 API 进行读/写交互，并且可以使用 Path 句柄构造，在这种情况下，
 * 它将通过 NIO.2 执行所有文件系统交互，只能依靠getFile()上的 File 。
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 28.12.2003
 * @see #FileSystemResource(String)
 * @see #FileSystemResource(File)
 * @see #FileSystemResource(Path)
 * @see java.io.File
 * @see java.nio.file.Files
 */
public class FileSystemResource extends AbstractResource implements WritableResource {

	private final String path;

	@Nullable
	private final File file;

	private final Path filePath;


	/**
	 * Create a new {@code FileSystemResource} from a file path.
	 * <p>Note: When building relative resources via {@link #createRelative},
	 * it makes a difference whether the specified resource base path here
	 * ends with a slash or not. In the case of "C:/dir1/", relative paths
	 * will be built underneath that root: for example, relative path "dir2" &rarr;
	 * "C:/dir1/dir2". In the case of "C:/dir1", relative paths will apply
	 * at the same directory level: relative path "dir2" &rarr; "C:/dir2".
	 *
	 * <p>从文件路径创建新的 FileSystemResource。
	 * <p>注意：通过 createRelative 构建相对资源时，此处指定的资源基路径是否以斜杠结尾会产生影响。
	 * 对于“C:/dir1/”，将在该根目录下构建相对路径：例如，相对路径“dir2”→“C:/dir1/dir2”。对于“C:/dir1”，
	 * 将在同一目录级别应用相对路径：相对路径“dir2”→“C:/dir2”。
	 * @param path a file path
	 * @see #FileSystemResource(Path)
	 */
	public FileSystemResource(String path) {
		Assert.notNull(path, "Path must not be null");
		this.path = StringUtils.cleanPath(path);
		this.file = new File(path);
		this.filePath = this.file.toPath();
	}

	/**
	 * Create a new {@code FileSystemResource} from a {@link File} handle.
	 * <p>Note: When building relative resources via {@link #createRelative},
	 * the relative path will apply <i>at the same directory level</i>:
	 * for example, new File("C:/dir1"), relative path "dir2" &rarr; "C:/dir2"!
	 * If you prefer to have relative paths built underneath the given root directory,
	 * use the {@link #FileSystemResource(String) constructor with a file path}
	 * to append a trailing slash to the root path: "C:/dir1/", which indicates
	 * this directory as root for all relative paths.
	 *
	 * <p>从文件句柄创建新的 FileSystemResource。
	 * <p>注意：通过 createRelative 构建相对资源时，相对路径将应用于同一目录级别：
	 * 例如，new File("C:/dir1")，相对路径“dir2”→“C:/dir2”！如果您希望在给定的根目录下构建相对路径，
	 * 请使用带有文件路径的构造函数在根路径后附加一个斜杠：“C:/dir1/”，表示此目录为所有相对路径的根目录。
	 * @param file a File handle
	 * @see #FileSystemResource(Path)
	 * @see #getFile()
	 */
	public FileSystemResource(File file) {
		Assert.notNull(file, "File must not be null");
		this.path = StringUtils.cleanPath(file.getPath());
		this.file = file;
		this.filePath = file.toPath();
	}

	/**
	 * Create a new {@code FileSystemResource} from a {@link Path} handle,
	 * performing all file system interactions via NIO.2 instead of {@link File}.
	 * <p>In contrast to {@link PathResource}, this variant strictly follows the
	 * general {@link FileSystemResource} conventions, in particular in terms of
	 * path cleaning and {@link #createRelative(String)} handling.
	 * <p>Note: When building relative resources via {@link #createRelative},
	 * the relative path will apply <i>at the same directory level</i>:
	 * for example, Paths.get("C:/dir1"), relative path "dir2" &rarr; "C:/dir2"!
	 * If you prefer to have relative paths built underneath the given root directory,
	 * use the {@link #FileSystemResource(String) constructor with a file path}
	 * to append a trailing slash to the root path: "C:/dir1/", which indicates
	 * this directory as root for all relative paths. Alternatively, consider
	 * using {@link PathResource#PathResource(Path)} for {@code java.nio.path.Path}
	 * resolution in {@code createRelative}, always nesting relative paths.
	 *
	 * <p>从 Path 句柄创建新的 FileSystemResource，通过 NIO.2 而不是 File 执行所有文件系统交互。
	 * <p>与 PathResource 相比，此变体严格遵循一般的 FileSystemResource 约定，
	 * 特别是在路径清理和 createRelative(String) 处理方面。
	 * <p>注意：通过 createRelative 构建相对资源时，相对路径将应用于同一目录级别：
	 * 例如，Paths.get("C:/dir1")，相对路径“dir2”→“C:/dir2”！如果您希望在给定的根目录下构建相对路径，
	 * 请使用带有文件路径的构造函数在根路径后附加一个尾部斜杠：“C:/dir1/”，这表示此目录是所有相对路径的根目录。
	 * 或者，考虑在 createRelative 中使用 PathResource.PathResource(Path) 进行 java.nio.path.Path 解析，
	 * 始终嵌套相对路径。
	 * @param filePath a Path handle to a file
	 * @since 5.1
	 * @see #FileSystemResource(File)
	 */
	public FileSystemResource(Path filePath) {
		Assert.notNull(filePath, "Path must not be null");
		this.path = StringUtils.cleanPath(filePath.toString());
		this.file = null;
		this.filePath = filePath;
	}

	/**
	 * Create a new {@code FileSystemResource} from a {@link FileSystem} handle,
	 * locating the specified path.
	 * <p>This is an alternative to {@link #FileSystemResource(String)},
	 * performing all file system interactions via NIO.2 instead of {@link File}.
	 *
	 * <p>从 FileSystem 句柄创建新的 FileSystemResource，定位指定路径。
	 * <p>这是 FileSystemResource(String) 的替代方案，通过 NIO.2 而不是 File 执行所有文件系统交互。
	 * @param fileSystem the FileSystem to locate the path within
	 * @param path a file path
	 * @since 5.1.1
	 * @see #FileSystemResource(File)
	 */
	public FileSystemResource(FileSystem fileSystem, String path) {
		Assert.notNull(fileSystem, "FileSystem must not be null");
		Assert.notNull(path, "Path must not be null");
		this.path = StringUtils.cleanPath(path);
		this.file = null;
		this.filePath = fileSystem.getPath(this.path).normalize();
	}


	/**
	 * Return the file path for this resource.
	 */
	public final String getPath() {
		return this.path;
	}

	/**
	 * This implementation returns whether the underlying file exists.
	 * @see java.io.File#exists()
	 * @see java.nio.file.Files#exists(Path, java.nio.file.LinkOption...)
	 */
	@Override
	public boolean exists() {
		return (this.file != null ? this.file.exists() : Files.exists(this.filePath));
	}

	/**
	 * This implementation checks whether the underlying file is marked as readable
	 * (and corresponds to an actual file with content, not to a directory).
	 * @see java.io.File#canRead()
	 * @see java.io.File#isDirectory()
	 * @see java.nio.file.Files#isReadable(Path)
	 * @see java.nio.file.Files#isDirectory(Path, java.nio.file.LinkOption...)
	 */
	@Override
	public boolean isReadable() {
		return (this.file != null ? this.file.canRead() && !this.file.isDirectory() :
				Files.isReadable(this.filePath) && !Files.isDirectory(this.filePath));
	}

	/**
	 * This implementation opens an NIO file stream for the underlying file.
	 * @see java.nio.file.Files#newInputStream(Path, java.nio.file.OpenOption...)
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		try {
			return Files.newInputStream(this.filePath);
		}
		catch (NoSuchFileException ex) {
			throw new FileNotFoundException(ex.getMessage());
		}
	}

	@Override
	public byte[] getContentAsByteArray() throws IOException {
		try {
			return Files.readAllBytes(this.filePath);
		}
		catch (NoSuchFileException ex) {
			throw new FileNotFoundException(ex.getMessage());
		}
	}

	@Override
	public String getContentAsString(Charset charset) throws IOException {
		try {
			return Files.readString(this.filePath, charset);
		}
		catch (NoSuchFileException ex) {
			throw new FileNotFoundException(ex.getMessage());
		}
	}

	/**
	 * This implementation checks whether the underlying file is marked as writable
	 * (and corresponds to an actual file with content, not to a directory).
	 * @see java.io.File#canWrite()
	 * @see java.io.File#isDirectory()
	 * @see java.nio.file.Files#isWritable(Path)
	 * @see java.nio.file.Files#isDirectory(Path, java.nio.file.LinkOption...)
	 */
	@Override
	public boolean isWritable() {
		return (this.file != null ? this.file.canWrite() && !this.file.isDirectory() :
				Files.isWritable(this.filePath) && !Files.isDirectory(this.filePath));
	}

	/**
	 * This implementation opens a FileOutputStream for the underlying file.
	 * @see java.nio.file.Files#newOutputStream(Path, java.nio.file.OpenOption...)
	 */
	@Override
	public OutputStream getOutputStream() throws IOException {
		return Files.newOutputStream(this.filePath);
	}

	/**
	 * This implementation returns a URL for the underlying file.
	 * @see java.io.File#toURI()
	 * @see java.nio.file.Path#toUri()
	 */
	@Override
	public URL getURL() throws IOException {
		return (this.file != null ? this.file.toURI().toURL() : this.filePath.toUri().toURL());
	}

	/**
	 * This implementation returns a URI for the underlying file.
	 * @see java.io.File#toURI()
	 * @see java.nio.file.Path#toUri()
	 */
	@Override
	public URI getURI() throws IOException {
		if (this.file != null) {
			return this.file.toURI();
		}
		else {
			URI uri = this.filePath.toUri();
			// Normalize URI? See https://github.com/spring-projects/spring-framework/issues/29275
			String scheme = uri.getScheme();
			if (ResourceUtils.URL_PROTOCOL_FILE.equals(scheme)) {
				try {
					uri = new URI(scheme, uri.getPath(), null);
				}
				catch (URISyntaxException ex) {
					throw new IOException("Failed to normalize URI: " + uri, ex);
				}
			}
			return uri;
		}
	}

	/**
	 * This implementation always indicates a file.
	 */
	@Override
	public boolean isFile() {
		return true;
	}

	/**
	 * This implementation returns the underlying File reference.
	 */
	@Override
	public File getFile() {
		return (this.file != null ? this.file : this.filePath.toFile());
	}

	/**
	 * This implementation opens a FileChannel for the underlying file.
	 * @see java.nio.channels.FileChannel
	 */
	@Override
	public ReadableByteChannel readableChannel() throws IOException {
		try {
			return FileChannel.open(this.filePath, StandardOpenOption.READ);
		}
		catch (NoSuchFileException ex) {
			throw new FileNotFoundException(ex.getMessage());
		}
	}

	/**
	 * This implementation opens a FileChannel for the underlying file.
	 * @see java.nio.channels.FileChannel
	 */
	@Override
	public WritableByteChannel writableChannel() throws IOException {
		return FileChannel.open(this.filePath, StandardOpenOption.WRITE);
	}

	/**
	 * This implementation returns the underlying File/Path length.
	 */
	@Override
	public long contentLength() throws IOException {
		if (this.file != null) {
			long length = this.file.length();
			if (length == 0L && !this.file.exists()) {
				throw new FileNotFoundException(getDescription() +
						" cannot be resolved in the file system for checking its content length");
			}
			return length;
		}
		else {
			try {
				return Files.size(this.filePath);
			}
			catch (NoSuchFileException ex) {
				throw new FileNotFoundException(ex.getMessage());
			}
		}
	}

	/**
	 * This implementation returns the underlying File/Path last-modified time.
	 */
	@Override
	public long lastModified() throws IOException {
		if (this.file != null) {
			return super.lastModified();
		}
		else {
			try {
				return Files.getLastModifiedTime(this.filePath).toMillis();
			}
			catch (NoSuchFileException ex) {
				throw new FileNotFoundException(ex.getMessage());
			}
		}
	}

	/**
	 * This implementation creates a FileSystemResource, applying the given path
	 * relative to the path of the underlying file of this resource descriptor.
	 * @see org.springframework.util.StringUtils#applyRelativePath(String, String)
	 */
	@Override
	public Resource createRelative(String relativePath) {
		String pathToUse = StringUtils.applyRelativePath(this.path, relativePath);
		return (this.file != null ? new FileSystemResource(pathToUse) :
				new FileSystemResource(this.filePath.getFileSystem(), pathToUse));
	}

	/**
	 * This implementation returns the name of the file.
	 * @see java.io.File#getName()
	 * @see java.nio.file.Path#getFileName()
	 */
	@Override
	public String getFilename() {
		return (this.file != null ? this.file.getName() : this.filePath.getFileName().toString());
	}

	/**
	 * This implementation returns a description that includes the absolute
	 * path of the file.
	 * @see java.io.File#getAbsolutePath()
	 * @see java.nio.file.Path#toAbsolutePath()
	 */
	@Override
	public String getDescription() {
		return "file [" + (this.file != null ? this.file.getAbsolutePath() : this.filePath.toAbsolutePath()) + "]";
	}


	/**
	 * This implementation compares the underlying file paths.
	 * @see #getPath()
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof FileSystemResource that && this.path.equals(that.path)));
	}

	/**
	 * This implementation returns the hash code of the underlying file path.
	 * @see #getPath()
	 */
	@Override
	public int hashCode() {
		return this.path.hashCode();
	}

}
