/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.core.metrics;

import java.util.function.Supplier;

import org.springframework.lang.Nullable;

/**
 * 步骤记录有关 ApplicationStartup 期间发生的特定阶段或操作的指标。
 * <p>StartupStep 的生命周期如下：
 * <ol>
 * <li>步骤通过调用应用程序启动来创建和启动，并分配一个唯一的 ID。
 * <li>然后我们可以在处理过程中使用 StartupStep.Tags 附加信息
 * <li>然后我们需要标记步骤的 end()
 * </ol>
 * <p>实现可以跟踪步骤的“执行时间”或其他指标。
 *
 * <p>Step recording metrics about a particular phase or action happening during the {@link ApplicationStartup}.
 *
 * <p>The lifecycle of a {@code StartupStep} goes as follows:
 * <ol>
 * <li>the step is created and starts by calling {@link ApplicationStartup#start(String) the application startup}
 * and is assigned a unique {@link StartupStep#getId() id}.
 * <li>we can then attach information with {@link Tags} during processing
 * <li>we then need to mark the {@link #end()} of the step
 * </ol>
 *
 * <p>Implementations can track the "execution time" or other metrics for steps.
 *
 * @author Brian Clozel
 * @since 5.3
 */
public interface StartupStep {

	/**
	 * 返回启动步骤的名称。
	 * <p>步骤名称描述当前操作或阶段。此技术名称应为“.”命名空间，并可重复用于描述应用程序启动
	 * 期间类似步骤的其他实例。
	 * <p>Return the name of the startup step.
	 * <p>A step name describes the current action or phase. This technical
	 * name should be "." namespaced and can be reused to describe other instances of
	 * similar steps during application startup.
	 */
	String getName();

	/**
	 * 返回应用程序启动过程中此步骤的唯一 ID。
	 * <p>Return the unique id for this step within the application startup.
	 */
	long getId();

	/**
	 * 如果可用，则返回父步骤的 ID。
	 * <p>父步骤是当前步骤创建时最近启动的步骤。
	 * <p>Return, if available, the id of the parent step.
	 * <p>The parent step is the step that was started the most recently
	 * when the current step was created.
	 */
	@Nullable
	Long getParentId();

	/**
	 * 向步骤添加 StartupStep.Tag。
	 * <p>Add a {@link Tag} to the step.
	 * @param key tag key
	 * @param value tag value
	 */
	StartupStep tag(String key, String value);

	/**
	 * 向步骤添加 StartupStep.Tag。
	 * <p>Add a {@link Tag} to the step.
	 * @param key tag key
	 * @param value {@link Supplier} for the tag value
	 */
	StartupStep tag(String key, Supplier<String> value);

	/**
	 * 返回此步骤的 StartupStep.Tag 集合。
	 * <p>Return the {@link Tag} collection for this step.
	 */
	Tags getTags();

	/**
	 * 记录步骤的状态以及可能的其他指标，例如执行时间。
	 * <p>一旦结束，不允许更改步骤状态。
	 * <p>Record the state of the step and possibly other metrics like execution time.
	 * <p>Once ended, changes on the step state are not allowed.
	 */
	void end();


	/**
	 * StartupStep.Tag 的不可变集合。
	 * <p>Immutable collection of {@link Tag}.
	 */
	interface Tags extends Iterable<Tag> {
	}


	/**
	 * 用于存储步骤元数据的简单键/值关联。
	 * <p>Simple key/value association for storing step metadata.
	 */
	interface Tag {

		/**
		 * 返回标签名称。
		 * <p>Return the {@code Tag} name.
		 */
		String getKey();

		/**
		 * 返回标签值。
		 * <p>Return the {@code Tag} value.
		 */
		String getValue();
	}

}
