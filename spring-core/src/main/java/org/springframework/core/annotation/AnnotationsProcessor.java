/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;

import org.springframework.lang.Nullable;

/**
 * Callback interface used to process annotations.
 * <p>用于处理注解的回调接口。
 * @author Phillip Webb
 * @since 5.2
 * @param <C> the context type
 * @param <R> the result type
 * @see AnnotationsScanner
 * @see TypeMappedAnnotations
 */
@FunctionalInterface
interface AnnotationsProcessor<C, R> {

	/**
	 * Called when an aggregate is about to be processed. This method may return
	 * a {@code non-null} result to short-circuit any further processing.
	 * <p>当聚合体即将被处理时调用。此方法可能会返回非空结果以缩短任何进一步的处理。
	 * @param context the context information relevant to the processor
	 * @param aggregateIndex the aggregate index about to be processed
	 * @return a {@code non-null} result if no further processing is required
	 */
	@Nullable
	default R doWithAggregate(C context, int aggregateIndex) {
		return null;
	}

	/**
	 * Called when an array of annotations can be processed. This method may
	 * return a {@code non-null} result to short-circuit any further processing.
	 * <p>当可以处理注解数组时调用。此方法可能会返回非空结果以缩短任何进一步的处理。
	 * null 表示继续搜索，用于遍历 annotations 来源的被注解元素的父类或其实现接口中的注解数组
	 * @param context the context information relevant to the processor
	 * @param aggregateIndex the aggregate index of the provided annotations
	 * @param source the original source of the annotations, if known
	 * @param annotations the annotations to process (this array may contain
	 * {@code null} elements)
	 * @return a {@code non-null} result if no further processing is required
	 */
	@Nullable
	R doWithAnnotations(C context, int aggregateIndex, @Nullable Object source, Annotation[] annotations);

	/**
	 * Get the final result to be returned. By default this method returns
	 * the last process result.
	 * <p>获取最终返回的结果，该方法默认返回最后一个处理结果。
	 * @param result the last early exit result, or {@code null} if none
	 * @return the final result to be returned to the caller
	 */
	@Nullable
	default R finish(@Nullable R result) {
		return result;
	}

}
