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

import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * CommandLinePropertySource 实现由简单的字符串数组支持。参数使用 SimpleCommandLineArgsParser解析得到CommandLineArgs
 * <h3>目的</h3>
 * <p>此CommandLinePropertySource实现旨在提供最简单的方法来解析命令行参数。与所有CommandLinePropertySource实现一样，
 * 命令行参数分为两个不同的组：选项参数和非选项参数，如下所述（从SimpleCommandLineArgsParser的 Javadoc 复制的一些部分） ：
 * <h3>使用选项参数</h3>
 * <p>选项参数必须遵守确切的语法：
 * <pre class="code">--optName[=optValue]</pre>
 *
 * <p>也就是说，选项必须以 "--" 为前缀，可以也可以不指定一个值。如果指定了值，则名称和值必须以不带空格的等号（“=”）分开。
 * 该值可以可选地是一个空字符串。
 * <h4>选项参数的有效示例</h4>
 * <pre class="code">
 * --foo
 * --foo=
 * --foo=""
 * --foo=bar
 * --foo="bar then baz"
 * --foo=bar,baz,biz</pre>
 * <h4>选项参数的无效示例</h4>
 * <pre class="code">
 * -foo
 * --foo bar
 * --foo = bar
 * --foo=bar --foo=baz --foo=biz</pre>
 *
 * <h3>选项参数结束</h3>
 * <p>底层解析器支持 POSIX“选项结束”分隔符，这意味着命令行中的任何"--" （空选项名称）都表明所有剩余参数都是非选项参数。
 * 例如， "--opt1=ignored" ， 以下命令行中的"--opt2"和"filename"是被认为是非选项参数。
 *  <pre class="code">
 *  --foo=bar -- --opt1=ignored -opt2 filename</pre>
 *
 *  <h3>使用非选项参数</h3>
 * <p>“选项结束”分隔符 ( -- ) 后面的任何参数或在没有“ -- ”选项前缀的情况下指定的任何参数都将被视为 “非选项参数”
 * 并通过 CommandLineArgs.getNonOptionArgs() 方法。
 *
 *  <h3>典型用法</h3>
 *  <pre class="code">
 * public static void main(String[] args) {
 *     PropertySource<?> ps = new SimpleCommandLinePropertySource(args);
 *     // ...
 * }</pre>
 * 有关完整的常规用法示例，请参阅 CommandLinePropertySource 。
 *
 *  <h3>超越基础知识</h3>
 * <p>当需要更全功能的命令行解析时，请考虑针对您选择的命令行解析库实现您自己的CommandLinePropertySource 。
 *
 * <p>{@link CommandLinePropertySource} implementation backed by a simple String array.
 *
 * <h3>Purpose</h3>
 * <p>This {@code CommandLinePropertySource} implementation aims to provide the simplest
 * possible approach to parsing command line arguments. As with all {@code
 * CommandLinePropertySource} implementations, command line arguments are broken into two
 * distinct groups: <em>option arguments</em> and <em>non-option arguments</em>, as
 * described below <em>(some sections copied from Javadoc for
 * {@link SimpleCommandLineArgsParser})</em>:
 *
 * <h3>Working with option arguments</h3>
 * <p>Option arguments must adhere to the exact syntax:
 *
 * <pre class="code">--optName[=optValue]</pre>
 *
 * <p>That is, options must be prefixed with "{@code --}" and may or may not
 * specify a value. If a value is specified, the name and value must be separated
 * <em>without spaces</em> by an equals sign ("="). The value may optionally be
 * an empty string.
 *
 * <h4>Valid examples of option arguments</h4>
 * <pre class="code">
 * --foo
 * --foo=
 * --foo=""
 * --foo=bar
 * --foo="bar then baz"
 * --foo=bar,baz,biz</pre>
 *
 * <h4>Invalid examples of option arguments</h4>
 * <pre class="code">
 * -foo
 * --foo bar
 * --foo = bar
 * --foo=bar --foo=baz --foo=biz</pre>
 *
 * <h3>End of option arguments</h3>
 * <p>The underlying parser supports the POSIX "end of options" delimiter, meaning
 * that any {@code "--"} (empty option name) in the command line signals that all
 * remaining arguments are non-option arguments. For example, {@code "--opt1=ignored"},
 * {@code "--opt2"}, and {@code "filename"} in the following command line are
 * considered non-option arguments.
 * <pre class="code">
 * --foo=bar -- --opt1=ignored -opt2 filename</pre>
 *
 * <h3>Working with non-option arguments</h3>
 * <p>Any arguments following the "end of options" delimiter ({@code --}) or
 * specified without the "{@code --}" option prefix will be considered as
 * "non-option arguments" and made available through the
 * {@link CommandLineArgs#getNonOptionArgs()} method.
 *
 * <h3>Typical usage</h3>
 * <pre class="code">
 * public static void main(String[] args) {
 *     PropertySource&lt;?&gt; ps = new SimpleCommandLinePropertySource(args);
 *     // ...
 * }</pre>
 *
 * See {@link CommandLinePropertySource} for complete general usage examples.
 *
 * <h3>Beyond the basics</h3>
 *
 * <p>When more fully-featured command line parsing is necessary, consider
 * implementing your own {@code CommandLinePropertySource} against the command line
 * parsing library of your choice.
 *
 * @author Chris Beams
 * @since 3.1
 * @see CommandLinePropertySource
 */
public class SimpleCommandLinePropertySource extends CommandLinePropertySource<CommandLineArgs> {

	/**
	 * 创建一个具有默认名称并由给定的命令行参数 String[] 支持的新 SimpleCommandLinePropertySource。
	 * <p>Create a new {@code SimpleCommandLinePropertySource} having the default name
	 * and backed by the given {@code String[]} of command line arguments.
	 * @see CommandLinePropertySource#COMMAND_LINE_PROPERTY_SOURCE_NAME
	 * @see CommandLinePropertySource#CommandLinePropertySource(Object)
	 */
	public SimpleCommandLinePropertySource(String... args) {
		super(new SimpleCommandLineArgsParser().parse(args));
	}

	/**
	 * Create a new {@code SimpleCommandLinePropertySource} having the given name
	 * and backed by the given {@code String[]} of command line arguments.
	 */
	public SimpleCommandLinePropertySource(String name, String[] args) {
		super(name, new SimpleCommandLineArgsParser().parse(args));
	}

	/**
	 * Get the property names for the option arguments.
	 */
	@Override
	public String[] getPropertyNames() {
		return StringUtils.toStringArray(this.source.getOptionNames());
	}

	@Override
	protected boolean containsOption(String name) {
		return this.source.containsOption(name);
	}

	@Override
	@Nullable
	protected List<String> getOptionValues(String name) {
		return this.source.getOptionValues(name);
	}

	@Override
	protected List<String> getNonOptionArgs() {
		return this.source.getNonOptionArgs();
	}

}
