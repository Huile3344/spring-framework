/*
 * Copyright 2002-2023 the original author or authors.
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * Provides {@link AnnotationTypeMapping} information for a single source
 * annotation type. Performs a recursive breadth first crawl of all
 * meta-annotations to ultimately provide a quick way to map the attributes of
 * a root {@link Annotation}.
 *
 * <p>Supports convention based merging of meta-annotations as well as implicit
 * and explicit {@link AliasFor @AliasFor} aliases. Also provides information
 * about mirrored attributes.
 *
 * <p>This class is designed to be cached so that meta-annotations only need to
 * be searched once, regardless of how many times they are actually used.
 *
 * <p>为单个源注解类型提供 AnnotationTypeMapping 信息。对所有元注解执行递归广度优先爬取，最终提供一种快速映射根注解属性的方法。
 * <p>支持基于约定的元注解合并以及隐式和显式 @AliasFor 别名。还提供有关镜像属性的信息。
 * <p>此类设计为缓存，因此只需搜索一次元注解，无论实际使用多少次。
 *
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 5.2
 * @see AnnotationTypeMapping
 */
final class AnnotationTypeMappings {

	private static final IntrospectionFailureLogger failureLogger = IntrospectionFailureLogger.DEBUG;

	// 基于Java 标准 @Repeatable 的可重复注解容器，key为 AnnotationFilter 的缓存
	private static final Map<AnnotationFilter, Cache> standardRepeatablesCache = new ConcurrentReferenceHashMap<>();

	// 基于不可重复注解容器，key 为 AnnotationFilter 的缓存
	private static final Map<AnnotationFilter, Cache> noRepeatablesCache = new ConcurrentReferenceHashMap<>();

	// 使用的可重复注解容器
	private final RepeatableContainers repeatableContainers;

	// 注解过滤器， 满足此过滤条件的注解信息将不会转换成 AnnotationTypeMapping，也不会存储到 mappings 中
	private final AnnotationFilter filter;

	// 存储此源注解的所有注解及其递归层级结构的所有元注解信息。每一个注解类型对应一个AnnotationTypeMapping 实例
	private final List<AnnotationTypeMapping> mappings;


	private AnnotationTypeMappings(RepeatableContainers repeatableContainers,
			AnnotationFilter filter, Class<? extends Annotation> annotationType,
			Set<Class<? extends Annotation>> visitedAnnotationTypes) {

		this.repeatableContainers = repeatableContainers;
		this.filter = filter;
		this.mappings = new ArrayList<>();
		addAllMappings(annotationType, visitedAnnotationTypes);
		this.mappings.forEach(AnnotationTypeMapping::afterAllMappingsSet);
	}


	/**
	 * 从根注解的注解类型解析出所有关联 AnnotationTypeMapping 的递归入口方法，针对每个注解类型生成一个 AnnotationTypeMapping 并放入队列中，
	 * 再从队列中取出注解，解析注解的元注解，然后为每个元注解的注解类型生成一个 AnnotationTypeMapping 并放入队列中，再解析其元注解，依次递归
	 *
	 * @param annotationType 根注解类型
	 * @param visitedAnnotationTypes
	 */
	private void addAllMappings(Class<? extends Annotation> annotationType,
			Set<Class<? extends Annotation>> visitedAnnotationTypes) {
		Deque<AnnotationTypeMapping> queue = new ArrayDeque<>();
		// 为注解 annotationType 生成一个根 AnnotationTypeMapping 对象，并放入队列 queue 中
		addIfPossible(queue, null, annotationType, null, visitedAnnotationTypes);
		// 从队列 queue 中取出 AnnotationTypeMapping 对象，并添加到 mappings 中
		while (!queue.isEmpty()) {
			AnnotationTypeMapping mapping = queue.removeFirst();
			this.mappings.add(mapping);
			// 为 AnnotationTypeMapping 对象的注解类型的元注解生成 AnnotationTypeMapping 对象，并放入队列 queue 中
			addMetaAnnotationsToQueue(queue, mapping);
		}
	}

	/**
	 * 将 AnnotationTypeMapping 类型的 source 对象的注解类型的元注解生成 AnnotationTypeMapping 对象，并放入队列 queue 中
	 * @param queue
	 * @param source
	 */
	private void addMetaAnnotationsToQueue(Deque<AnnotationTypeMapping> queue, AnnotationTypeMapping source) {
		// 返回此注解Class的直接存在的注解数组，忽略继承的注解
		Annotation[] metaAnnotations = AnnotationsScanner.getDeclaredAnnotations(source.getAnnotationType(), false);
		for (Annotation metaAnnotation : metaAnnotations) {
			// 如果 metaAnnotation 不符合 filter 的过滤条件，或者不是"java.lang"包和"org.springframework.lang"包中的注解类、
			// 或者已经被映射处理过的注解类（内部会递归其 source），则跳过
			if (!isMappable(source, metaAnnotation)) {
				continue;
			}
			// 如果 metaAnnotation 是可重复容器注解，则获取 metaAnnotation 的所有可重复注解
			Annotation[] repeatedAnnotations = this.repeatableContainers.findRepeatedAnnotations(metaAnnotation);
			if (repeatedAnnotations != null) {
				for (Annotation repeatedAnnotation : repeatedAnnotations) {
					if (!isMappable(source, repeatedAnnotation)) {
						continue;
					}
					// 将可重复注解 repeatedAnnotation 逐一转换成 AnnotationTypeMapping 添加到队列 queue 中
					addIfPossible(queue, source, repeatedAnnotation);
				}
			}
			else {
				// 如果 metaAnnotation 不是可重复注解，则直接转换成 AnnotationTypeMapping 添加到队列 queue 中
				addIfPossible(queue, source, metaAnnotation);
			}
		}
	}

	private void addIfPossible(Deque<AnnotationTypeMapping> queue, AnnotationTypeMapping source, Annotation ann) {
		// 此时 visitedAnnotationTypes 是一个新的 Set 集合，表明每一个元注解共用一个 Set 集合
		addIfPossible(queue, source, ann.annotationType(), ann, new HashSet<>());
	}

	private void addIfPossible(Deque<AnnotationTypeMapping> queue, @Nullable AnnotationTypeMapping source,
			Class<? extends Annotation> annotationType, @Nullable Annotation ann,
			Set<Class<? extends Annotation>> visitedAnnotationTypes) {

		try {
			// 基于注解 ann 和 AnnotationTypeMapping 的 source ，创建注解的Class对象的 annotationType 对应的 AnnotationTypeMapping
			queue.addLast(new AnnotationTypeMapping(source, annotationType, ann, visitedAnnotationTypes));
		}
		catch (Exception ex) {
			AnnotationUtils.rethrowAnnotationConfigurationException(ex);
			if (failureLogger.isEnabled()) {
				failureLogger.log("Failed to introspect meta-annotation " + annotationType.getName(),
						(source != null ? source.getAnnotationType() : null), ex);
			}
		}
	}

	private boolean isMappable(AnnotationTypeMapping source, @Nullable Annotation metaAnnotation) {
		return (metaAnnotation != null && !this.filter.matches(metaAnnotation) &&
				!AnnotationFilter.PLAIN.matches(source.getAnnotationType()) &&
				!isAlreadyMapped(source, metaAnnotation));
	}

	private boolean isAlreadyMapped(AnnotationTypeMapping source, Annotation metaAnnotation) {
		Class<? extends Annotation> annotationType = metaAnnotation.annotationType();
		AnnotationTypeMapping mapping = source;
		while (mapping != null) {
			if (mapping.getAnnotationType() == annotationType) {
				return true;
			}
			mapping = mapping.getSource();
		}
		return false;
	}

	/**
	 * Get the total number of contained mappings.
	 * @return the total number of mappings
	 */
	int size() {
		return this.mappings.size();
	}

	/**
	 * Get an individual mapping from this instance.
	 * <p>Index {@code 0} will always return the root mapping; higher indexes
	 * will return meta-annotation mappings.
	 * @param index the index to return
	 * @return the {@link AnnotationTypeMapping}
	 * @throws IndexOutOfBoundsException if the index is out of range
	 * ({@code index < 0 || index >= size()})
	 */
	AnnotationTypeMapping get(int index) {
		return this.mappings.get(index);
	}


	/**
	 * Create {@link AnnotationTypeMappings} for the specified annotation type.
	 * @param annotationType the source annotation type
	 * @return type mappings for the annotation type
	 */
	static AnnotationTypeMappings forAnnotationType(Class<? extends Annotation> annotationType) {
		return forAnnotationType(annotationType, new HashSet<>());
	}

	/**
	 * Create {@link AnnotationTypeMappings} for the specified annotation type.
	 * @param annotationType the source annotation type
	 * @param visitedAnnotationTypes the set of annotations that we have already
	 * visited; used to avoid infinite recursion for recursive annotations which
	 * some JVM languages support (such as Kotlin)
	 * @return type mappings for the annotation type
	 */
	static AnnotationTypeMappings forAnnotationType(Class<? extends Annotation> annotationType,
			Set<Class<? extends Annotation>> visitedAnnotationTypes) {

		return forAnnotationType(annotationType, RepeatableContainers.standardRepeatables(),
				AnnotationFilter.PLAIN, visitedAnnotationTypes);
	}

	/**
	 * Create {@link AnnotationTypeMappings} for the specified annotation type.
	 * @param annotationType the source annotation type
	 * @param repeatableContainers the repeatable containers that may be used by
	 * the meta-annotations
	 * @param annotationFilter the annotation filter used to limit which
	 * annotations are considered
	 * @return type mappings for the annotation type
	 */
	static AnnotationTypeMappings forAnnotationType(Class<? extends Annotation> annotationType,
			RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter) {

		return forAnnotationType(annotationType, repeatableContainers, annotationFilter, new HashSet<>());
	}

	/**
	 * Create {@link AnnotationTypeMappings} for the specified annotation type.
	 * @param annotationType the source annotation type
	 * @param repeatableContainers the repeatable containers that may be used by
	 * the meta-annotations
	 * @param annotationFilter the annotation filter used to limit which
	 * annotations are considered
	 * @param visitedAnnotationTypes the set of annotations that we have already
	 * visited; used to avoid infinite recursion for recursive annotations which
	 * some JVM languages support (such as Kotlin)
	 * @return type mappings for the annotation type
	 */
	static AnnotationTypeMappings forAnnotationType(Class<? extends Annotation> annotationType,
			RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter,
			Set<Class<? extends Annotation>> visitedAnnotationTypes) {

		if (repeatableContainers == RepeatableContainers.standardRepeatables()) {
			return standardRepeatablesCache.computeIfAbsent(annotationFilter,
					key -> new Cache(repeatableContainers, key)).get(annotationType, visitedAnnotationTypes);
		}
		if (repeatableContainers == RepeatableContainers.none()) {
			return noRepeatablesCache.computeIfAbsent(annotationFilter,
					key -> new Cache(repeatableContainers, key)).get(annotationType, visitedAnnotationTypes);
		}
		return new AnnotationTypeMappings(repeatableContainers, annotationFilter, annotationType,
				visitedAnnotationTypes);
	}

	static void clearCache() {
		standardRepeatablesCache.clear();
		noRepeatablesCache.clear();
	}


	/**
	 * Cache created per {@link AnnotationFilter}.
	 * <p>为每个 AnnotationFilter 创建的缓存
	 */
	private static class Cache {

		private final RepeatableContainers repeatableContainers;

		private final AnnotationFilter filter;

		private final Map<Class<? extends Annotation>, AnnotationTypeMappings> mappings;

		/**
		 * Create a cache instance with the specified filter.
		 * @param filter the annotation filter
		 */
		Cache(RepeatableContainers repeatableContainers, AnnotationFilter filter) {
			this.repeatableContainers = repeatableContainers;
			this.filter = filter;
			this.mappings = new ConcurrentReferenceHashMap<>();
		}

		/**
		 * Get or create {@link AnnotationTypeMappings} for the specified annotation type.
		 * @param annotationType the annotation type
		 * @param visitedAnnotationTypes the set of annotations that we have already
		 * visited; used to avoid infinite recursion for recursive annotations which
		 * some JVM languages support (such as Kotlin)
		 * @return a new or existing {@link AnnotationTypeMappings} instance
		 */
		AnnotationTypeMappings get(Class<? extends Annotation> annotationType,
				Set<Class<? extends Annotation>> visitedAnnotationTypes) {
			return this.mappings.computeIfAbsent(annotationType, key -> createMappings(key, visitedAnnotationTypes));
		}

		private AnnotationTypeMappings createMappings(Class<? extends Annotation> annotationType,
				Set<Class<? extends Annotation>> visitedAnnotationTypes) {
			return new AnnotationTypeMappings(this.repeatableContainers, this.filter, annotationType,
					visitedAnnotationTypes);
		}
	}

}
