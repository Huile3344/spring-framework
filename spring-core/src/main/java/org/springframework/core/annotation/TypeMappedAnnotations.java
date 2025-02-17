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
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.lang.Nullable;

/**
 * {@link MergedAnnotations} implementation that searches for and adapts
 * annotations and meta-annotations using {@link AnnotationTypeMappings}.
 * <p> 使用 AnnotationTypeMappings 搜索和调整注解和元注解的 MergedAnnotations 实现。
 *
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 5.2
 */
final class TypeMappedAnnotations implements MergedAnnotations {

	/**
	 * Shared instance that can be used when there are no annotations.
	 * <p>当没有注解时可以使用的共享实例
	 */
	static final MergedAnnotations NONE = new TypeMappedAnnotations(
			null, new Annotation[0], RepeatableContainers.none(), AnnotationFilter.ALL);


	// element 不为 null 时，和 element 字段是同一个对象值
	@Nullable
	private final Object source;

	// Class 或 Method 或 Constructor 或 Field 等可被注解元素
	@Nullable
	private final AnnotatedElement element;

	// 搜索策略；当 annotations 有值时不需要使用该字段
	@Nullable
	private final SearchStrategy searchStrategy;

	// 搜索封装类的过滤条件，一般是都搜索或都不搜索；当 annotations 有值时不需要使用该字段， 策略是 Search.never
	private final Predicate<Class<?>> searchEnclosingClass;

	// 若 annotations 为 null ， 则通过 searchStrategy 和 searchEnclosingClass 搜索 element 中的注解信息生成 Aggregate
	@Nullable
	private final Annotation[] annotations;

	// 处理可重复容器注解使用的可重复注解容器
	private final RepeatableContainers repeatableContainers;

	// 注解过滤器，忽略掉某些注解的处理
	private final AnnotationFilter annotationFilter;

	// 聚合体列表，每个层次结构中的一个 AnnotatedElement 的所有注解对应一个 Aggregate 实例，形成一个列表
	@Nullable
	private volatile List<Aggregate> aggregates;


	private TypeMappedAnnotations(AnnotatedElement element, SearchStrategy searchStrategy,
			Predicate<Class<?>> searchEnclosingClass, RepeatableContainers repeatableContainers,
			AnnotationFilter annotationFilter) {

		this.source = element;
		this.element = element;
		this.searchStrategy = searchStrategy;
		this.searchEnclosingClass = searchEnclosingClass;
		this.annotations = null;
		this.repeatableContainers = repeatableContainers;
		this.annotationFilter = annotationFilter;
	}

	private TypeMappedAnnotations(@Nullable Object source, Annotation[] annotations,
			RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter) {

		this.source = source;
		this.element = null;
		this.searchStrategy = null;
		this.searchEnclosingClass = Search.never;
		this.annotations = annotations;
		this.repeatableContainers = repeatableContainers;
		this.annotationFilter = annotationFilter;
	}


	@Override
	public <A extends Annotation> boolean isPresent(Class<A> annotationType) {
		if (this.annotationFilter.matches(annotationType)) {
			return false;
		}
		return Boolean.TRUE.equals(scan(annotationType,
				IsPresent.get(this.repeatableContainers, this.annotationFilter, false)));
	}

	@Override
	public boolean isPresent(String annotationType) {
		if (this.annotationFilter.matches(annotationType)) {
			return false;
		}
		return Boolean.TRUE.equals(scan(annotationType,
				IsPresent.get(this.repeatableContainers, this.annotationFilter, false)));
	}

	@Override
	public <A extends Annotation> boolean isDirectlyPresent(Class<A> annotationType) {
		if (this.annotationFilter.matches(annotationType)) {
			return false;
		}
		return Boolean.TRUE.equals(scan(annotationType,
				IsPresent.get(this.repeatableContainers, this.annotationFilter, true)));
	}

	@Override
	public boolean isDirectlyPresent(String annotationType) {
		if (this.annotationFilter.matches(annotationType)) {
			return false;
		}
		return Boolean.TRUE.equals(scan(annotationType,
				IsPresent.get(this.repeatableContainers, this.annotationFilter, true)));
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

		if (this.annotationFilter.matches(annotationType)) {
			return MergedAnnotation.missing();
		}
		MergedAnnotation<A> result = scan(annotationType,
				new MergedAnnotationFinder<>(annotationType, predicate, selector));
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

		if (this.annotationFilter.matches(annotationType)) {
			return MergedAnnotation.missing();
		}
		MergedAnnotation<A> result = scan(annotationType,
				new MergedAnnotationFinder<>(annotationType, predicate, selector));
		return (result != null ? result : MergedAnnotation.missing());
	}

	@Override
	public <A extends Annotation> Stream<MergedAnnotation<A>> stream(Class<A> annotationType) {
		if (this.annotationFilter == AnnotationFilter.ALL) {
			return Stream.empty();
		}
		return StreamSupport.stream(spliterator(annotationType), false);
	}

	@Override
	public <A extends Annotation> Stream<MergedAnnotation<A>> stream(String annotationType) {
		if (this.annotationFilter == AnnotationFilter.ALL) {
			return Stream.empty();
		}
		return StreamSupport.stream(spliterator(annotationType), false);
	}

	@Override
	public Stream<MergedAnnotation<Annotation>> stream() {
		if (this.annotationFilter == AnnotationFilter.ALL) {
			return Stream.empty();
		}
		return StreamSupport.stream(spliterator(), false);
	}

	@Override
	public Iterator<MergedAnnotation<Annotation>> iterator() {
		if (this.annotationFilter == AnnotationFilter.ALL) {
			return Collections.emptyIterator();
		}
		return Spliterators.iterator(spliterator());
	}

	@Override
	public Spliterator<MergedAnnotation<Annotation>> spliterator() {
		if (this.annotationFilter == AnnotationFilter.ALL) {
			return Spliterators.emptySpliterator();
		}
		return spliterator(null);
	}

	private <A extends Annotation> Spliterator<MergedAnnotation<A>> spliterator(@Nullable Object annotationType) {
		return new AggregatesSpliterator<>(annotationType, getAggregates());
	}

	private List<Aggregate> getAggregates() {
		List<Aggregate> aggregates = this.aggregates;
		if (aggregates == null) {
			aggregates = scan(this, new AggregatesCollector());
			if (aggregates == null || aggregates.isEmpty()) {
				aggregates = Collections.emptyList();
			}
			this.aggregates = aggregates;
		}
		return aggregates;
	}

	@Nullable
	private <C, R> R scan(C criteria, AnnotationsProcessor<C, R> processor) {
		if (this.annotations != null) {
			R result = processor.doWithAnnotations(criteria, 0, this.source, this.annotations);
			return processor.finish(result);
		}
		if (this.element != null && this.searchStrategy != null) {
			// 扫描可被注解元素的层次结构以查找相关注解，并调用处理器处理注解形成结果
			return AnnotationsScanner.scan(criteria, this.element, this.searchStrategy,
					this.searchEnclosingClass, processor);
		}
		return null;
	}


	static MergedAnnotations from(AnnotatedElement element, SearchStrategy searchStrategy,
			Predicate<Class<?>> searchEnclosingClass, RepeatableContainers repeatableContainers,
			AnnotationFilter annotationFilter) {

		if (AnnotationsScanner.isKnownEmpty(element, searchStrategy, searchEnclosingClass)) {
			return NONE;
		}
		return new TypeMappedAnnotations(element, searchStrategy, searchEnclosingClass, repeatableContainers, annotationFilter);
	}

	static MergedAnnotations from(@Nullable Object source, Annotation[] annotations,
			RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter) {

		if (annotations.length == 0) {
			return NONE;
		}
		return new TypeMappedAnnotations(source, annotations, repeatableContainers, annotationFilter);
	}

	private static boolean isMappingForType(AnnotationTypeMapping mapping,
			AnnotationFilter annotationFilter, @Nullable Object requiredType) {

		Class<? extends Annotation> actualType = mapping.getAnnotationType();
		return (!annotationFilter.matches(actualType) &&
				(requiredType == null || actualType == requiredType || actualType.getName().equals(requiredType)));
	}


	/**
	 * {@link AnnotationsProcessor} used to detect if an annotation is directly
	 * present or meta-present.
	 * <p>用于检测注解是直接存在还是元存在的 AnnotationsProcessor 。
	 */
	private static final class IsPresent implements AnnotationsProcessor<Object, Boolean> {

		/**
		 * Shared instances that save us needing to create a new processor for
		 * the common combinations.
		 * <p>共享实例使我们无需为常见组合创建新的处理器。
		 */
		private static final IsPresent[] SHARED;
		static {
			SHARED = new IsPresent[4];
			SHARED[0] = new IsPresent(RepeatableContainers.none(), AnnotationFilter.PLAIN, true);
			SHARED[1] = new IsPresent(RepeatableContainers.none(), AnnotationFilter.PLAIN, false);
			SHARED[2] = new IsPresent(RepeatableContainers.standardRepeatables(), AnnotationFilter.PLAIN, true);
			SHARED[3] = new IsPresent(RepeatableContainers.standardRepeatables(), AnnotationFilter.PLAIN, false);
		}

		// 用于处理可重复容器注解使用的可重复注解容器
		private final RepeatableContainers repeatableContainers;

		// 用于过滤掉不需要处理的注解的过滤器，值一般都是 AnnotationFilter.PLAIN 用于过滤掉 "java.lang", "org.springframework.lang" 包及其子包中的注解
		private final AnnotationFilter annotationFilter;

		// 是否仅做直接存在的注解的判断
		private final boolean directOnly;

		private IsPresent(RepeatableContainers repeatableContainers,
				AnnotationFilter annotationFilter, boolean directOnly) {

			this.repeatableContainers = repeatableContainers;
			this.annotationFilter = annotationFilter;
			this.directOnly = directOnly;
		}

		/**
		 * 判断目标注解类型 requiredType 是否存在于 annotations 注解数组中，若当前对象（IsPresent）的 directOnly 为 true，仅做直接存在判断，否则 directOnly 为 false，则做直接存在和元存在判断
		 * @param requiredType 目标注解类型， Class 或 Class 的名称
		 * @param aggregateIndex 未使用的参数
		 * @param source 未使用的参数
		 * @param annotations 由于判断目标注解类型是否存在的数据源注解数组
		 * @return 目标注解类型是否存在，若存在，则返回 ture， annotations 中不存在则返回 null，并继续搜索，
		 * 用于遍历 annotations 来源被注解元素的父类或其实现接口中的注解数组
		 */
		@Override
		@Nullable
		public Boolean doWithAnnotations(Object requiredType, int aggregateIndex,
				@Nullable Object source, Annotation[] annotations) {

			for (Annotation annotation : annotations) {
				if (annotation != null) {
					Class<? extends Annotation> type = annotation.annotationType();
					if (type != null && !this.annotationFilter.matches(type)) {
						// requiredType 和 type 的类型或类型的名称是否相等
						if (type == requiredType || type.getName().equals(requiredType)) {
							// 表示可被注解元素的注解和从超类中继承的注解中有匹配的注解类型
							return Boolean.TRUE;
						}
						// 若注解是可重复容器注解， 通过可重复注解容器，可获取可重复容器注解中的可重复注解数组，否则返回 null
						Annotation[] repeatedAnnotations =
								this.repeatableContainers.findRepeatedAnnotations(annotation);
						if (repeatedAnnotations != null) {
							// 判断可重复注解数组是否作为需要查询的注解类型，若匹配则认为可重复注解直接存在
							Boolean result = doWithAnnotations(
									requiredType, aggregateIndex, source, repeatedAnnotations);
							if (result != null) {
								return result;
							}
						}
						// 如果不是直接存在，则通过注解类型映射，判断是否为元注解类型存在
						if (!this.directOnly) {
							AnnotationTypeMappings mappings = AnnotationTypeMappings.forAnnotationType(type);
							for (int i = 0; i < mappings.size(); i++) {
								AnnotationTypeMapping mapping = mappings.get(i);
								if (isMappingForType(mapping, this.annotationFilter, requiredType)) {
									// 表示可被注解元素的元注解中有匹配的注解类型
									return Boolean.TRUE;
								}
							}
						}
					}
				}
			}
			// 返回null，表示继续搜索，用于遍历 annotations 来源的可被注解元素的超类或超接口中的注解数组
			return null;
		}

		static IsPresent get(RepeatableContainers repeatableContainers,
				AnnotationFilter annotationFilter, boolean directOnly) {

			// Use a single shared instance for common combinations
			if (annotationFilter == AnnotationFilter.PLAIN) {
				if (repeatableContainers == RepeatableContainers.none()) {
					return SHARED[directOnly ? 0 : 1];
				}
				if (repeatableContainers == RepeatableContainers.standardRepeatables()) {
					return SHARED[directOnly ? 2 : 3];
				}
			}
			return new IsPresent(repeatableContainers, annotationFilter, directOnly);
		}
	}


	/**
	 * {@link AnnotationsProcessor} that finds a single {@link MergedAnnotation}.
	 * <p>找到单个 MergedAnnotation 的 AnnotationsProcessor。
	 */
	private class MergedAnnotationFinder<A extends Annotation>
			implements AnnotationsProcessor<Object, MergedAnnotation<A>> {

		// 目标注解类型， Class 或 Class 的名称
		private final Object requiredType;

		// MergedAnnotation 过滤器，仅保留满足条件的 MergedAnnotation
		@Nullable
		private final Predicate<? super MergedAnnotation<A>> predicate;

		// MergedAnnotation 选择器，用于选择最优的 MergedAnnotation
		private final MergedAnnotationSelector<A> selector;

		// 查找后的结果
		@Nullable
		private MergedAnnotation<A> result;

		MergedAnnotationFinder(Object requiredType, @Nullable Predicate<? super MergedAnnotation<A>> predicate,
				@Nullable MergedAnnotationSelector<A> selector) {

			this.requiredType = requiredType;
			this.predicate = predicate;
			this.selector = (selector != null ? selector : MergedAnnotationSelectors.nearest());
		}

		@Override
		@Nullable
		public MergedAnnotation<A> doWithAggregate(Object context, int aggregateIndex) {
			// 若层次结构中有多个类或接口都有相同对应的注解类信息，则取第一个类中有指定注解的注解信息。而一个类中有多个指定注解，将由 MergedAnnotationSelector 筛选
			return this.result;
		}

		/**
		 * 通过处理注解数组 annotations，获取当前对象（MergedAnnotationFinder）的 requiredType 字段指定的注解类型最匹配的注解，并转换为 MergedAnnotation
		 * @param type 未使用的参数，注解类型
		 * @param aggregateIndex 仅传递，用于创建 MergedAnnotation
		 * @param source 仅传递，用于创建 MergedAnnotation
		 * @param annotations 包含实际数据的注解数组，其中一个用于创建 MergedAnnotation，可用于获取实际的属性值
		 * @return MergedAnnotation 对象或 null，null 表示继续搜索，用于遍历 annotations 来源被注解元素的父类或其实现接口中的注解数组
		 */
		@Override
		@Nullable
		public MergedAnnotation<A> doWithAnnotations(Object type, int aggregateIndex,
				@Nullable Object source, Annotation[] annotations) {

			for (Annotation annotation : annotations) {
				// 过滤掉不需要处理的注解
				if (annotation != null && !annotationFilter.matches(annotation)) {
					MergedAnnotation<A> result = process(type, aggregateIndex, source, annotation);
					if (result != null) {
						// 找到结果，无需再查找后续内容
						return result;
					}
				}
			}
			// 返回 null，表示继续搜索，用于遍历 annotations 来源的可被注解元素的超类或超接口中的注解数组
			return null;
		}

		/**
		 * 通过处理注解数组 annotations，获取当前对象（MergedAnnotationFinder）的 requiredType 字段指定的注解类型匹配的注解，并转换为 MergedAnnotation
		 * @param type 未使用的参数，注解类型
		 * @param aggregateIndex 仅传递，用于创建 MergedAnnotation
		 * @param source 仅传递，用于创建 MergedAnnotation
		 * @param annotation 包含实际数据的注解，用于创建 MergedAnnotation，可用于获取实际的属性值
		 * @return MergedAnnotation 对象或 null
		 */
		@Nullable
		private MergedAnnotation<A> process(
				Object type, int aggregateIndex, @Nullable Object source, Annotation annotation) {

			// 通过可重复注解容器，获取注解的可重复注解数组
			Annotation[] repeatedAnnotations = repeatableContainers.findRepeatedAnnotations(annotation);
			if (repeatedAnnotations != null) {
				// 判断注解的可重复注解数组是否作为需要查询的注解类型，若匹配则将生成可重复注解的 MergedAnnotation 作为结果
				MergedAnnotation<A> result = doWithAnnotations(type, aggregateIndex, source, repeatedAnnotations);
				if (result != null) {
					// 找到结果，无需再查找后续内容
					return result;
				}
			}
			// 查询注解及其元注解中是否有需要查询的注解类型，若匹配则将生成匹配的注解的 MergedAnnotation 作为候选结果，且多个候选结果进行筛选
			AnnotationTypeMappings mappings = AnnotationTypeMappings.forAnnotationType(
					annotation.annotationType(), repeatableContainers, annotationFilter);
			for (int i = 0; i < mappings.size(); i++) {
				AnnotationTypeMapping mapping = mappings.get(i);
				if (isMappingForType(mapping, annotationFilter, this.requiredType)) {
					MergedAnnotation<A> candidate = TypeMappedAnnotation.createIfPossible(
							mapping, source, annotation, aggregateIndex, IntrospectionFailureLogger.INFO);
					if (candidate != null && (this.predicate == null || this.predicate.test(candidate))) {
						// 若选择器找到最优结果
						if (this.selector.isBestCandidate(candidate)) {
							// 找到最优结果，无需再查找后续内容
							return candidate;
						}
						// 选择器从已保存的结果和候选结果，选择一个最优结果保留
						updateLastResult(candidate);
					}
				}
			}
			return null;
		}

		private void updateLastResult(MergedAnnotation<A> candidate) {
			MergedAnnotation<A> lastResult = this.result;
			// 选择器从已保存的结果和候选结果，选择一个最优结果保留
			this.result = (lastResult != null ? this.selector.select(lastResult, candidate) : candidate);
		}

		@Override
		@Nullable
		public MergedAnnotation<A> finish(@Nullable MergedAnnotation<A> result) {
			return (result != null ? result : this.result);
		}
	}


	/**
	 * {@link AnnotationsProcessor} that collects {@link Aggregate} instances.
	 * <p>收集 TypeMappedAnnotations.Aggregate 实例的 AnnotationsProcessor。
	 */
	private class AggregatesCollector implements AnnotationsProcessor<Object, List<Aggregate>> {

		private final List<Aggregate> aggregates = new ArrayList<>();

		/**
		 * 往 aggregates 雷彪中添加 Aggregate 对象
		 * @param criteria 未使用的参数，是 TypeMappedAnnotations 实例
		 * @param aggregateIndex the aggregate index of the provided annotations
		 * @param source the original source of the annotations, if known
		 * @param annotations the annotations to process (this array may contain {@code null} elements)
		 * @return 永远返回null，表示继续搜索，用于遍历 annotations 来源被注解元素的父类或其实现接口中的注解数组
		 */
		@Override
		@Nullable
		public List<Aggregate> doWithAnnotations(Object criteria, int aggregateIndex,
				@Nullable Object source, Annotation[] annotations) {

			this.aggregates.add(createAggregate(aggregateIndex, source, annotations));
			// 返回 null，表示继续搜索，用于遍历 annotations 来源的可被注解元素的超类或超接口中的注解数组，继续添加到 aggregates 中，此时 aggregateIndex 一般会递增
			return null;
		}

		private Aggregate createAggregate(int aggregateIndex, @Nullable Object source, Annotation[] annotations) {
			List<Annotation> aggregateAnnotations = getAggregateAnnotations(annotations);
			return new Aggregate(aggregateIndex, source, aggregateAnnotations);
		}

		private List<Annotation> getAggregateAnnotations(Annotation[] annotations) {
			List<Annotation> result = new ArrayList<>(annotations.length);
			addAggregateAnnotations(result, annotations);
			return result;
		}

		/**
		 * 往 aggregateAnnotations 列表中添加 Annotation 对象，将 annotations 中的可重复容器注解转换成可重复注解数组，并移除不需要处理的注解
		 * @param aggregateAnnotations
		 * @param annotations
		 */
		private void addAggregateAnnotations(List<Annotation> aggregateAnnotations, Annotation[] annotations) {
			for (Annotation annotation : annotations) {
				if (annotation != null && !annotationFilter.matches(annotation)) {
					Annotation[] repeatedAnnotations = repeatableContainers.findRepeatedAnnotations(annotation);
					if (repeatedAnnotations != null) {
						addAggregateAnnotations(aggregateAnnotations, repeatedAnnotations);
					}
					else {
						aggregateAnnotations.add(annotation);
					}
				}
			}
		}

		@Override
		public List<Aggregate> finish(@Nullable List<Aggregate> processResult) {
			return this.aggregates;
		}
	}


	/**
	 * 聚合体
	 */
	private static class Aggregate {

		// 仅用于创建 TypeMappedAnnotation 时，指定 TypeMappedAnnotation.aggregateIndex 的聚合索引 aggregateIndex
		private final int aggregateIndex;

		// 仅用于创建 TypeMappedAnnotation 时，指定 TypeMappedAnnotation.source 的源 source
		@Nullable
		private final Object source;

		// 数据源注解数组，可用于创建 TypeMappedAnnotation，每个注解可指定为一个 TypeMappedAnnotation 实例的 TypeMappedAnnotation.rootAttributes 字段值
		private final List<Annotation> annotations;

		// 和 annotations 中注解一一对应对应的 AnnotationTypeMappings，AnnotationTypeMappings 中的一个 AnnotationTypeMapping 可用于创建 TypeMappedAnnotation 时指定为 TypeMappedAnnotation.mapping
		private final AnnotationTypeMappings[] mappings;

		Aggregate(int aggregateIndex, @Nullable Object source, List<Annotation> annotations) {
			this.aggregateIndex = aggregateIndex;
			this.source = source;
			this.annotations = annotations;
			this.mappings = new AnnotationTypeMappings[annotations.size()];
			for (int i = 0; i < annotations.size(); i++) {
				this.mappings[i] = AnnotationTypeMappings.forAnnotationType(annotations.get(i).annotationType());
			}
		}

		int size() {
			return this.annotations.size();
		}

		@Nullable
		AnnotationTypeMapping getMapping(int annotationIndex, int mappingIndex) {
			AnnotationTypeMappings mappings = getMappings(annotationIndex);
			return (mappingIndex < mappings.size() ? mappings.get(mappingIndex) : null);
		}

		AnnotationTypeMappings getMappings(int annotationIndex) {
			return this.mappings[annotationIndex];
		}

		@Nullable
		<A extends Annotation> MergedAnnotation<A> createMergedAnnotationIfPossible(
				int annotationIndex, int mappingIndex, IntrospectionFailureLogger logger) {

			return TypeMappedAnnotation.createIfPossible(
					this.mappings[annotationIndex].get(mappingIndex), this.source,
					this.annotations.get(annotationIndex), this.aggregateIndex, logger);
		}
	}


	/**
	 * {@link Spliterator} used to consume merged annotations from the
	 * aggregates in distance fist order.
	 * <p>用于按距离优先顺序使用来自聚合体的合并注解的Spliterator 。
	 */
	private class AggregatesSpliterator<A extends Annotation> implements Spliterator<MergedAnnotation<A>> {

		// 目标注解类型，null 表示所有注解类型
		@Nullable
		private final Object requiredType;

		private final List<Aggregate> aggregates;

		// 聚合体索引位游标，用于遍历 aggregates 时，记录当前的索引位
		private int aggregateCursor;

		// mappingCursors 数组，用于记录当前 aggregateCursor 对应的 aggregate 中，每个对应的注解数组的 AnnotationTypeMappings 数组中，遍历的 AnnotationTypeMapping 的索引位
		@Nullable
		private int[] mappingCursors;

		AggregatesSpliterator(@Nullable Object requiredType, List<Aggregate> aggregates) {
			this.requiredType = requiredType;
			this.aggregates = aggregates;
			this.aggregateCursor = 0;
		}

		// 迭代递归时将调用此方法
		@Override
		public boolean tryAdvance(Consumer<? super MergedAnnotation<A>> action) {
			while (this.aggregateCursor < this.aggregates.size()) {
				Aggregate aggregate = this.aggregates.get(this.aggregateCursor);
				if (tryAdvance(aggregate, action)) {
					return true;
				}
				this.aggregateCursor++;
				this.mappingCursors = null;
			}
			return false;
		}

		private boolean tryAdvance(Aggregate aggregate, Consumer<? super MergedAnnotation<A>> action) {
			if (this.mappingCursors == null) {
				this.mappingCursors = new int[aggregate.size()];
			}
			int lowestDistance = Integer.MAX_VALUE;
			int annotationResult = -1;
			for (int annotationIndex = 0; annotationIndex < aggregate.size(); annotationIndex++) {
				// 当 mappingCursors 中记录的 AnnotationTypeMapping 的索引位大于 aggregate 的 AnnotationTypeMappings 中 AnnotationTypeMapping 的大小时，
				// 将会返回 null，即已经遍历完当前 AnnotationTypeMappings 中所有的 AnnotationTypeMapping
				AnnotationTypeMapping mapping = getNextSuitableMapping(aggregate, annotationIndex);
				if (mapping != null && mapping.getDistance() < lowestDistance) {
					annotationResult = annotationIndex;
					lowestDistance = mapping.getDistance();
				}
				if (lowestDistance == 0) {
					break;
				}
			}
			if (annotationResult != -1) {
				MergedAnnotation<A> mergedAnnotation = aggregate.createMergedAnnotationIfPossible(
						annotationResult, this.mappingCursors[annotationResult],
						this.requiredType != null ? IntrospectionFailureLogger.INFO : IntrospectionFailureLogger.DEBUG);
				this.mappingCursors[annotationResult]++;
				if (mergedAnnotation == null) {
					return tryAdvance(aggregate, action);
				}
				action.accept(mergedAnnotation);
				return true;
			}
			return false;
		}

		@Nullable
		private AnnotationTypeMapping getNextSuitableMapping(Aggregate aggregate, int annotationIndex) {
			int[] cursors = this.mappingCursors;
			if (cursors != null) {
				AnnotationTypeMapping mapping;
				do {
					// 当 mappingCursors 中记录的 AnnotationTypeMapping 的索引位大于 AnnotationTypeMappings 中 AnnotationTypeMapping 的大小时，
					// 将会返回 null，即已经遍历完 AnnotationTypeMappings 中所有的 AnnotationTypeMapping
					mapping = aggregate.getMapping(annotationIndex, cursors[annotationIndex]);
					if (mapping != null && isMappingForType(mapping, annotationFilter, this.requiredType)) {
						return mapping;
					}
					cursors[annotationIndex]++;
				}
				while (mapping != null);
			}
			return null;
		}

		@Override
		@Nullable
		public Spliterator<MergedAnnotation<A>> trySplit() {
			return null;
		}

		@Override
		public long estimateSize() {
			int size = 0;
			for (int aggregateIndex = this.aggregateCursor;
					aggregateIndex < this.aggregates.size(); aggregateIndex++) {
				Aggregate aggregate = this.aggregates.get(aggregateIndex);
				for (int annotationIndex = 0; annotationIndex < aggregate.size(); annotationIndex++) {
					AnnotationTypeMappings mappings = aggregate.getMappings(annotationIndex);
					int numberOfMappings = mappings.size();
					if (aggregateIndex == this.aggregateCursor && this.mappingCursors != null) {
						numberOfMappings -= Math.min(this.mappingCursors[annotationIndex], mappings.size());
					}
					size += numberOfMappings;
				}
			}
			return size;
		}

		@Override
		public int characteristics() {
			return NONNULL | IMMUTABLE;
		}
	}

}
