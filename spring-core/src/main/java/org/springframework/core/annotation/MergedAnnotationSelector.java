/*
 * Copyright 2002-2019 the original author or authors.
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

/**
 * Strategy interface used to select between two {@link MergedAnnotation}
 * instances.
 * <p>用于在两个 MergedAnnotation 实例之间进行选择的策略接口
 * @author Phillip Webb
 * @since 5.2
 * @param <A> the annotation type
 * @see MergedAnnotationSelectors
 */
@FunctionalInterface
public interface MergedAnnotationSelector<A extends Annotation> {

	/**
	 * Determine if the existing annotation is known to be the best
	 * candidate and any subsequent selections may be skipped.
	 * <p>确定传入的 MergedAnnotation 是否是最佳候选者；若是，则取当前的 MergedAnnotation 作为最优解，跳过任何后续筛选；
	 * 否则将继续从 MergedAnnotations 中筛选其他 MergedAnnotation ，并通过 {@link #select} 方法俩俩对比出最优解。
	 * @param annotation the annotation to check
	 * @return {@code true} if the annotation is known to be the best candidate
	 */
	default boolean isBestCandidate(MergedAnnotation<A> annotation) {
		return false;
	}

	/**
	 * Select the annotation that should be used.
	 * <p>从已存在的 MergedAnnotation 和候选的 MergedAnnotation 中选择要使用的最优解 MergedAnnotation
	 *
	 * @param existing an existing annotation returned from an earlier result
	 * @param candidate a candidate annotation that may be better suited
	 * @return the most appropriate annotation from the {@code existing} or
	 * {@code candidate}
	 */
	MergedAnnotation<A> select(MergedAnnotation<A> existing, MergedAnnotation<A> candidate);

}
