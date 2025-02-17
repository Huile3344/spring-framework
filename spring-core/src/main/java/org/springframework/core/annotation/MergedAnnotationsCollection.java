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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link MergedAnnotations} implementation backed by a {@link Collection} of
 * {@link MergedAnnotation} instances that represent direct annotations.
 *
 * <p>由代表直接注解的 MergedAnnotation 实例集合支持的 MergedAnnotations 实现
 *
 * @author Phillip Webb
 * @since 5.2
 * @see MergedAnnotations#of(Collection)
 */
final class MergedAnnotationsCollection implements MergedAnnotations {

	// 每个注解对应的 MergedAnnotation 形成的数组
	private final MergedAnnotation<?>[] annotations;

	// 和 MergedAnnotation 一一对应的 AnnotationTypeMappings 形成的数组
	private final AnnotationTypeMappings[] mappings;


	private MergedAnnotationsCollection(Collection<MergedAnnotation<?>> annotations) {
		Assert.notNull(annotations, "Annotations must not be null");
		this.annotations = annotations.toArray(new MergedAnnotation<?>[0]);
		this.mappings = new AnnotationTypeMappings[this.annotations.length];
		for (int i = 0; i < this.annotations.length; i++) {
			MergedAnnotation<?> annotation = this.annotations[i];
			Assert.notNull(annotation, "Annotation must not be null");
			Assert.isTrue(annotation.isDirectlyPresent(), "Annotation must be directly present");
			Assert.isTrue(annotation.getAggregateIndex() == 0, "Annotation must have aggregate index of zero");
			this.mappings[i] = AnnotationTypeMappings.forAnnotationType(annotation.getType());
		}
	}


	@Override
	public Iterator<MergedAnnotation<Annotation>> iterator() {
		return Spliterators.iterator(spliterator());
	}

	@Override
	public Spliterator<MergedAnnotation<Annotation>> spliterator() {
		return spliterator(null);
	}

	private <A extends Annotation> Spliterator<MergedAnnotation<A>> spliterator(@Nullable Object annotationType) {
		return new AnnotationsSpliterator<>(annotationType);
	}

	@Override
	public <A extends Annotation> boolean isPresent(Class<A> annotationType) {
		return isPresent(annotationType, false);
	}

	@Override
	public boolean isPresent(String annotationType) {
		return isPresent(annotationType, false);
	}

	@Override
	public <A extends Annotation> boolean isDirectlyPresent(Class<A> annotationType) {
		return isPresent(annotationType, true);
	}

	@Override
	public boolean isDirectlyPresent(String annotationType) {
		return isPresent(annotationType, true);
	}

	/**
	 * 判断注解类型是否存在，依据 directOnly 判断直接存在还是直接存在和元存在
	 * @param requiredType
	 * @param directOnly
	 * @return
	 */
	private boolean isPresent(Object requiredType, boolean directOnly) {
		for (MergedAnnotation<?> annotation : this.annotations) {
			Class<? extends Annotation> type = annotation.getType();
			if (type == requiredType || type.getName().equals(requiredType)) {
				return true;
			}
		}
		if (!directOnly) {
			// 针对非直接注解，遍历 AnnotationTypeMappings 中的所有 AnnotationTypeMapping 的注解类型是否匹配
			for (AnnotationTypeMappings mappings : this.mappings) {
				for (int i = 1; i < mappings.size(); i++) {
					AnnotationTypeMapping mapping = mappings.get(i);
					// 判断 mapping 中的注解类型是否和 requiredType 匹配
					if (isMappingForType(mapping, requiredType)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public <A extends Annotation> MergedAnnotation<A> get(Class<A> annotationType) {
		return get(annotationType, null, null);
	}

	@Override
	public <A extends Annotation> MergedAnnotation<A> get(Class<A> annotationType,
			@Nullable Predicate<? super MergedAnnotation<A>> predicate) {

		return get(annotationType, predicate, null);
	}

	@Override
	public <A extends Annotation> MergedAnnotation<A> get(Class<A> annotationType,
			@Nullable Predicate<? super MergedAnnotation<A>> predicate,
			@Nullable MergedAnnotationSelector<A> selector) {

		MergedAnnotation<A> result = find(annotationType, predicate, selector);
		return (result != null ? result : MergedAnnotation.missing());
	}

	@Override
	public <A extends Annotation> MergedAnnotation<A> get(String annotationType) {
		return get(annotationType, null, null);
	}

	@Override
	public <A extends Annotation> MergedAnnotation<A> get(String annotationType,
			@Nullable Predicate<? super MergedAnnotation<A>> predicate) {

		return get(annotationType, predicate, null);
	}

	@Override
	public <A extends Annotation> MergedAnnotation<A> get(String annotationType,
			@Nullable Predicate<? super MergedAnnotation<A>> predicate,
			@Nullable MergedAnnotationSelector<A> selector) {

		MergedAnnotation<A> result = find(annotationType, predicate, selector);
		return (result != null ? result : MergedAnnotation.missing());
	}

	/**
	 * 根据注解类型、选择策略和过滤条件查询最优的指定注解类型的 MergedAnnotation 实例
	 * @param requiredType
	 * @param predicate
	 * @param selector
	 * @return
	 * @param <A>
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	private <A extends Annotation> MergedAnnotation<A> find(Object requiredType,
			@Nullable Predicate<? super MergedAnnotation<A>> predicate,
			@Nullable MergedAnnotationSelector<A> selector) {

		if (selector == null) {
			selector = MergedAnnotationSelectors.nearest();
		}

		MergedAnnotation<A> result = null;
		for (int i = 0; i < this.annotations.length; i++) {
			MergedAnnotation<?> root = this.annotations[i];
			if (root != null) {
				AnnotationTypeMappings mappings = this.mappings[i];
				for (int mappingIndex = 0; mappingIndex < mappings.size(); mappingIndex++) {
					AnnotationTypeMapping mapping = mappings.get(mappingIndex);
					// 判断注解类型是否匹配
					if (!isMappingForType(mapping, requiredType)) {
						continue;
					}
					MergedAnnotation<A> candidate = (mappingIndex == 0 ? (MergedAnnotation<A>) root :
							TypeMappedAnnotation.createIfPossible(mapping, root, IntrospectionFailureLogger.INFO));
					if (candidate != null && (predicate == null || predicate.test(candidate))) {
						// 如果 candidate 符合条件，则判断是否为最佳候选者
						if (selector.isBestCandidate(candidate)) {
							return candidate;
						}
						// 如果 candidate 不是最佳候选者，则判断是否需要替换当前结果
						result = (result != null ? selector.select(result, candidate) : candidate);
					}
				}
			}
		}
		return result;
	}

	@Override
	public <A extends Annotation> Stream<MergedAnnotation<A>> stream(Class<A> annotationType) {
		return StreamSupport.stream(spliterator(annotationType), false);
	}

	@Override
	public <A extends Annotation> Stream<MergedAnnotation<A>> stream(String annotationType) {
		return StreamSupport.stream(spliterator(annotationType), false);
	}

	@Override
	public Stream<MergedAnnotation<Annotation>> stream() {
		return StreamSupport.stream(spliterator(), false);
	}

	private static boolean isMappingForType(AnnotationTypeMapping mapping, @Nullable Object requiredType) {
		if (requiredType == null) {
			return true;
		}
		Class<? extends Annotation> actualType = mapping.getAnnotationType();
		return (actualType == requiredType || actualType.getName().equals(requiredType));
	}

	/**
	 * MergedAnnotationsCollection 类仅有的静态工厂方法用于生成新的 MergedAnnotationsCollection 实例
	 *
	 * @param annotations
	 * @return
	 */
	static MergedAnnotations of(Collection<MergedAnnotation<?>> annotations) {
		Assert.notNull(annotations, "Annotations must not be null");
		if (annotations.isEmpty()) {
			return TypeMappedAnnotations.NONE;
		}
		return new MergedAnnotationsCollection(annotations);
	}


	private class AnnotationsSpliterator<A extends Annotation> implements Spliterator<MergedAnnotation<A>> {

		@Nullable
		private final Object requiredType;

		private final int[] mappingCursors;

		public AnnotationsSpliterator(@Nullable Object requiredType) {
			this.mappingCursors = new int[annotations.length];
			this.requiredType = requiredType;
		}

		/**
		 * 遍历所有注解，并按距离，依次将当前未遍历的注解中距离最小的注解放入迭代器中
		 * @param action The action
		 * @return
		 */
		@Override
		public boolean tryAdvance(Consumer<? super MergedAnnotation<A>> action) {
			int lowestDistance = Integer.MAX_VALUE;
			int annotationResult = -1;
			// 遍历所有注解，并按距离，依次将当前未遍历的注解中距离最小，排在最前面的注解放入迭代器中，在注解数组中的索引位，以及在 AnnotationTypeMappings 中的索引位
			for (int annotationIndex = 0; annotationIndex < annotations.length; annotationIndex++) {
				// 当 mappingCursors 中记录的 AnnotationTypeMapping 的索引位大于 AnnotationTypeMappings 中 AnnotationTypeMapping 的大小时，
				// 将会返回 null，即已经遍历完当前 AnnotationTypeMappings 中所有的 AnnotationTypeMapping
				AnnotationTypeMapping mapping = getNextSuitableMapping(annotationIndex);
				if (mapping != null && mapping.getDistance() < lowestDistance) {
					annotationResult = annotationIndex;
					lowestDistance = mapping.getDistance();
				}
				if (lowestDistance == 0) {
					break;
				}
			}
			if (annotationResult != -1) {
				MergedAnnotation<A> mergedAnnotation = createMergedAnnotationIfPossible(
						annotationResult, this.mappingCursors[annotationResult]);
				this.mappingCursors[annotationResult]++;
				if (mergedAnnotation == null) {
					return tryAdvance(action);
				}
				action.accept(mergedAnnotation);
				return true;
			}
			return false;
		}

		@Nullable
		private AnnotationTypeMapping getNextSuitableMapping(int annotationIndex) {
			AnnotationTypeMapping mapping;
			do {
				// 当 mappingCursors 中记录的 AnnotationTypeMapping 的索引位大于 AnnotationTypeMappings 中 AnnotationTypeMapping 的大小时，
				// 将会返回 null，即已经遍历完 AnnotationTypeMappings 中所有的 AnnotationTypeMapping
				mapping = getMapping(annotationIndex, this.mappingCursors[annotationIndex]);
				if (mapping != null && isMappingForType(mapping, this.requiredType)) {
					return mapping;
				}
				// 对于 mappingCursors 中记录的 AnnotationTypeMapping 的索引位超过实际长度的，继续递增索引位
				this.mappingCursors[annotationIndex]++;
			}
			while (mapping != null);
			return null;
		}

		@Nullable
		private AnnotationTypeMapping getMapping(int annotationIndex, int mappingIndex) {
			AnnotationTypeMappings mappings = MergedAnnotationsCollection.this.mappings[annotationIndex];
			return (mappingIndex < mappings.size() ? mappings.get(mappingIndex) : null);
		}

		@Nullable
		@SuppressWarnings("unchecked")
		private MergedAnnotation<A> createMergedAnnotationIfPossible(int annotationIndex, int mappingIndex) {
			MergedAnnotation<?> root = annotations[annotationIndex];
			if (mappingIndex == 0) {
				return (MergedAnnotation<A>) root;
			}
			IntrospectionFailureLogger logger = (this.requiredType != null ?
					IntrospectionFailureLogger.INFO : IntrospectionFailureLogger.DEBUG);
			return TypeMappedAnnotation.createIfPossible(
					mappings[annotationIndex].get(mappingIndex), root, logger);
		}

		@Override
		@Nullable
		public Spliterator<MergedAnnotation<A>> trySplit() {
			return null;
		}

		/**
		 * 剩余待遍历的数量
		 * @return
		 */
		@Override
		public long estimateSize() {
			int size = 0;
			for (int i = 0; i < annotations.length; i++) {
				AnnotationTypeMappings mappings = MergedAnnotationsCollection.this.mappings[i];
				int numberOfMappings = mappings.size();
				numberOfMappings -= Math.min(this.mappingCursors[i], mappings.size());
				size += numberOfMappings;
			}
			return size;
		}

		@Override
		public int characteristics() {
			return NONNULL | IMMUTABLE;
		}
	}

}
