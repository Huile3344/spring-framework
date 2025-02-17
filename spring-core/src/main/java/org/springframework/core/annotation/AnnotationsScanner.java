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
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotations.Search;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Scanner to search for relevant annotations in the annotation hierarchy of an
 * {@link AnnotatedElement}.
 *
 * <p>用于在 {@link AnnotatedElement} 的注解层次结构中搜索相关注解的扫描器（Scanner）
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 5.2
 * @see AnnotationsProcessor
 */
abstract class AnnotationsScanner {

	private static final Annotation[] NO_ANNOTATIONS = {};

	private static final Method[] NO_METHODS = {};

	// 缓存 AnnotatedElement 到 Annotation[] 映射的缓存，用于存储如 Class、Method、Constructor、Field 等可被注解元素上声明的注解数组的缓存
	private static final Map<AnnotatedElement, Annotation[]> declaredAnnotationCache =
			new ConcurrentReferenceHashMap<>(256);

	private static final Map<Class<?>, Method[]> baseTypeMethodsCache =
			new ConcurrentReferenceHashMap<>(256);


	private AnnotationsScanner() {
	}


	/**
	 * Scan the hierarchy of the specified element for relevant annotations and
	 * call the processor as required.
	 * <p>扫描指定可被注解元素的层次结构以查找相关注解，并根据需要调用处理器。
	 * @param context an optional context object that will be passed back to the
	 * processor
	 * @param source the source element to scan
	 * @param searchStrategy the search strategy to use
	 * @param searchEnclosingClass a predicate which evaluates to {@code true}
	 * if a search should be performed on the enclosing class of the class
	 * supplied to the predicate
	 * @param processor the processor that receives the annotations
	 * @return the result of {@link AnnotationsProcessor#finish(Object)}
	 */
	@Nullable
	static <C, R> R scan(C context, AnnotatedElement source, SearchStrategy searchStrategy,
			Predicate<Class<?>> searchEnclosingClass, AnnotationsProcessor<C, R> processor) {

		R result = process(context, source, searchStrategy, searchEnclosingClass, processor);
		return processor.finish(result);
	}

	/**
	 * 通过指定搜索策略搜索指定可被注解元素上的注解数组信息，并将注解数组信息传递给注解处理器，进行注解处理返回指定类型的结果
	 * @param context
	 * @param source
	 * @param searchStrategy
	 * @param searchEnclosingClass
	 * @param processor
	 * @return
	 * @param <C>
	 * @param <R>
	 */
	@Nullable
	private static <C, R> R process(C context, AnnotatedElement source,
			SearchStrategy searchStrategy, Predicate<Class<?>> searchEnclosingClass,
			AnnotationsProcessor<C, R> processor) {

		if (source instanceof Class<?> clazz) {
			return processClass(context, clazz, searchStrategy, searchEnclosingClass, processor);
		}
		if (source instanceof Method method) {
			return processMethod(context, method, searchStrategy, processor);
		}
		return processElement(context, source, processor);
	}

	/**
	 * 通过指定搜索策略搜索指定 Class 上的注解数组信息，并将注解数组信息传递给注解处理器，进行注解处理返回指定类型的结果
	 * @param context
	 * @param source
	 * @param searchStrategy
	 * @param searchEnclosingClass
	 * @param processor
	 * @return
	 * @param <C>
	 * @param <R>
	 */
	@Nullable
	private static <C, R> R processClass(C context, Class<?> source, SearchStrategy searchStrategy,
			Predicate<Class<?>> searchEnclosingClass, AnnotationsProcessor<C, R> processor) {

		return switch (searchStrategy) {
			case DIRECT -> processElement(context, source, processor);
			case INHERITED_ANNOTATIONS -> processClassInheritedAnnotations(context, source, processor);
			case SUPERCLASS -> processClassHierarchy(context, source, processor, false, Search.never);
			case TYPE_HIERARCHY -> processClassHierarchy(context, source, processor, true, searchEnclosingClass);
		};
	}

	/**
	 * 获取类上的注解数组信息（包括可继承注解），并将注解数组信息传递给注解处理器，进行注解处理返回指定类型的结果
	 * @param context
	 * @param source
	 * @param processor
	 * @return
	 * @param <C>
	 * @param <R>
	 */
	@Nullable
	private static <C, R> R processClassInheritedAnnotations(C context, Class<?> source,
			AnnotationsProcessor<C, R> processor) {

		try {
			if (isWithoutHierarchy(source, Search.never)) {
				return processElement(context, source, processor);
			}
			Annotation[] relevant = null;
			int remaining = Integer.MAX_VALUE;
			int aggregateIndex = 0;
			Class<?> root = source;
			while (source != null && source != Object.class && remaining > 0 && !hasPlainJavaAnnotationsOnly(source)) {
				R result = processor.doWithAggregate(context, aggregateIndex);
				if (result != null) {
					return result;
				}
				Annotation[] declaredAnns = getDeclaredAnnotations(source, true);
				if (declaredAnns.length > 0) {
					if (relevant == null) {
						// 返回 root 上直接声明的注解和从超类（接口无效）上继承下来的注解（即注解被 @Inherited 所注解）
						relevant = root.getAnnotations();
						remaining = relevant.length;
					}
					for (int i = 0; i < declaredAnns.length; i++) {
						if (declaredAnns[i] != null) {
							boolean isRelevant = false;
							for (int relevantIndex = 0; relevantIndex < relevant.length; relevantIndex++) {
								if (relevant[relevantIndex] != null &&
										declaredAnns[i].annotationType() == relevant[relevantIndex].annotationType()) {
									isRelevant = true;
									// 若父子类中都有可继承的注解，将以最子类的注解为准，也就是此处置为null的原因，避免父类中重复的可继承注解又被解析污染数据
									relevant[relevantIndex] = null;
									remaining--;
									break;
								}
							}
							// 不相关的注解将会被清理掉
							if (!isRelevant) {
								declaredAnns[i] = null;
							}
						}
					}
				}
				result = processor.doWithAnnotations(context, aggregateIndex, source, declaredAnns);
				if (result != null) {
					return result;
				}
				// 递归查找父类
				source = source.getSuperclass();
				aggregateIndex++;
			}
		}
		catch (Throwable ex) {
			AnnotationUtils.handleIntrospectionFailure(source, ex);
		}
		return null;
	}

	@Nullable
	private static <C, R> R processClassHierarchy(C context, Class<?> source,
			AnnotationsProcessor<C, R> processor, boolean includeInterfaces,
			Predicate<Class<?>> searchEnclosingClass) {

		return processClassHierarchy(context, new int[] {0}, source, processor,
				includeInterfaces, searchEnclosingClass);
	}

	/**
	 * 获取当前类上的注解数组信息，包括类层次结构上接口和超类的注解，基于深度优先先接口后超类的递归方式，
	 * 并将注解数组信息传递给注解处理器，进行注解处理返回指定类型的结果
	 * @param context
	 * @param aggregateIndex
	 * @param source
	 * @param processor
	 * @param includeInterfaces
	 * @param searchEnclosingClass
	 * @return
	 * @param <C>
	 * @param <R>
	 */
	@Nullable
	private static <C, R> R processClassHierarchy(C context, int[] aggregateIndex, Class<?> source,
			AnnotationsProcessor<C, R> processor, boolean includeInterfaces,
			Predicate<Class<?>> searchEnclosingClass) {

		try {
			R result = processor.doWithAggregate(context, aggregateIndex[0]);
			if (result != null) {
				return result;
			}
			if (hasPlainJavaAnnotationsOnly(source)) {
				return null;
			}
			// 获取当前类的直接注解数组
			Annotation[] annotations = getDeclaredAnnotations(source, false);
			// 由注解处理器处理注解数组，并获取结果
			result = processor.doWithAnnotations(context, aggregateIndex[0], source, annotations);
			if (result != null) {
				return result;
			}
			aggregateIndex[0]++;
			// 获取当前类直接实现的接口的注解，并交由注解处理器处理，并获取结果
			if (includeInterfaces) {
				for (Class<?> interfaceType : source.getInterfaces()) {
					R interfacesResult = processClassHierarchy(context, aggregateIndex,
						interfaceType, processor, true, searchEnclosingClass);
					if (interfacesResult != null) {
						return interfacesResult;
					}
				}
			}
			// 获取当前类的父类的注解，并交由注解处理器处理，并获取结果
			Class<?> superclass = source.getSuperclass();
			if (superclass != Object.class && superclass != null) {
				R superclassResult = processClassHierarchy(context, aggregateIndex,
					superclass, processor, includeInterfaces, searchEnclosingClass);
				if (superclassResult != null) {
					return superclassResult;
				}
			}
			if (searchEnclosingClass.test(source)) {
				// Since merely attempting to load the enclosing class may result in
				// automatic loading of sibling nested classes that in turn results
				// in an exception such as NoClassDefFoundError, we wrap the following
				// in its own dedicated try-catch block in order not to preemptively
				// halt the annotation scanning process.
				// 由于仅仅尝试加载封闭类可能会导致自动加载同级嵌套类，进而导致诸如 NoClassDefFoundError 之类的异常，
				// 因此将以下内容包装在其自己的专用 try-catch 块中，以免抢先停止注解扫描过程。
				try {
					// 获取封装类
					Class<?> enclosingClass = source.getEnclosingClass();
					if (enclosingClass != null) {
						R enclosingResult = processClassHierarchy(context, aggregateIndex,
							enclosingClass, processor, includeInterfaces, searchEnclosingClass);
						if (enclosingResult != null) {
							return enclosingResult;
						}
					}
				}
				catch (Throwable ex) {
					AnnotationUtils.handleIntrospectionFailure(source, ex);
				}
			}
		}
		catch (Throwable ex) {
			AnnotationUtils.handleIntrospectionFailure(source, ex);
		}
		return null;
	}

	/**
	 * 通过指定搜索策略搜索指定 Method 上的注解数组信息，并将注解数组信息传递给注解处理器，进行注解处理返回指定类型的结果
	 * @param context
	 * @param source
	 * @param searchStrategy
	 * @param processor
	 * @return
	 * @param <C>
	 * @param <R>
	 */
	@Nullable
	private static <C, R> R processMethod(C context, Method source,
			SearchStrategy searchStrategy, AnnotationsProcessor<C, R> processor) {

		return switch (searchStrategy) {
			case DIRECT, INHERITED_ANNOTATIONS -> processMethodInheritedAnnotations(context, source, processor);
			case SUPERCLASS -> processMethodHierarchy(context, new int[]{0}, source.getDeclaringClass(),
					processor, source, false);
			case TYPE_HIERARCHY -> processMethodHierarchy(context, new int[]{0}, source.getDeclaringClass(),
					processor, source, true);
		};
	}

	@Nullable
	private static <C, R> R processMethodInheritedAnnotations(C context, Method source,
			AnnotationsProcessor<C, R> processor) {

		try {
			R result = processor.doWithAggregate(context, 0);
			return (result != null ? result :
				processMethodAnnotations(context, 0, source, processor));
		}
		catch (Throwable ex) {
			AnnotationUtils.handleIntrospectionFailure(source, ex);
		}
		return null;
	}

	/**
	 * 获取指定 Method 上的注解数组信息，包括类层次结构上接口的覆写方法和超类的覆写方法的注解，基于深度优先先接口后超类的递归方式，
	 * 并将注解数组信息传递给注解处理器，进行注解处理返回指定类型的结果
	 * @param context
	 * @param aggregateIndex
	 * @param sourceClass
	 * @param processor
	 * @param rootMethod
	 * @param includeInterfaces
	 * @return
	 * @param <C>
	 * @param <R>
	 */
	@Nullable
	private static <C, R> R processMethodHierarchy(C context, int[] aggregateIndex,
			Class<?> sourceClass, AnnotationsProcessor<C, R> processor, Method rootMethod,
			boolean includeInterfaces) {

		try {
			R result = processor.doWithAggregate(context, aggregateIndex[0]);
			if (result != null) {
				return result;
			}
			if (hasPlainJavaAnnotationsOnly(sourceClass)) {
				return null;
			}
			boolean calledProcessor = false;
			// 首次递归调用 processMethodHierarchy 方法时，此条件成立。后续 sourceClass 就是原始类的超类或超接口
			if (sourceClass == rootMethod.getDeclaringClass()) {
				// 获取 rootMethod 上的注解数组信息，并将注解数组信息传递给注解处理器，进行注解处理返回指定类型的结果
				result = processMethodAnnotations(context, aggregateIndex[0],
					rootMethod, processor);
				calledProcessor = true;
				if (result != null) {
					return result;
				}
			}
			else {
				// sourceClass 是原始类的超类或超接口， 而 candidateMethod 是中 sourceClass 中声明的所有非私有方法或接口的默认方法，且方法有业务注解信息
				for (Method candidateMethod : getBaseTypeMethods(context, sourceClass)) {
					// 判断 rootMethod 是否是超类或超接口方法 candidateMethod 的覆写方法
					if (candidateMethod != null && isOverride(rootMethod, candidateMethod)) {
						// 获取 candidateMethod 上的注解数组信息，并将注解数组信息传递给注解处理器，进行注解处理返回指定类型的结果
						result = processMethodAnnotations(context, aggregateIndex[0],
							candidateMethod, processor);
						calledProcessor = true;
						if (result != null) {
							return result;
						}
					}
				}
			}
			// 私有方法无法覆写，直接结束接口和超类的搜索
			if (Modifier.isPrivate(rootMethod.getModifiers())) {
				return null;
			}
			if (calledProcessor) {
				aggregateIndex[0]++;
			}
			if (includeInterfaces) {
				for (Class<?> interfaceType : sourceClass.getInterfaces()) {
					// 处理接口的覆写方法的注解信息
					R interfacesResult = processMethodHierarchy(context, aggregateIndex,
						interfaceType, processor, rootMethod, true);
					if (interfacesResult != null) {
						return interfacesResult;
					}
				}
			}
			Class<?> superclass = sourceClass.getSuperclass();
			if (superclass != Object.class && superclass != null) {
				// 处理超类的覆写方法的注解信息
				R superclassResult = processMethodHierarchy(context, aggregateIndex,
					superclass, processor, rootMethod, includeInterfaces);
				if (superclassResult != null) {
					return superclassResult;
				}
			}
		}
		catch (Throwable ex) {
			AnnotationUtils.handleIntrospectionFailure(rootMethod, ex);
		}
		return null;
	}

	/**
	 * 获取 baseType 类中所有非私有，且有业务注解的所有方法
	 * @param context
	 * @param baseType
	 * @return
	 * @param <C>
	 */
	private static <C> Method[] getBaseTypeMethods(C context, Class<?> baseType) {
		if (baseType == Object.class || hasPlainJavaAnnotationsOnly(baseType)) {
			return NO_METHODS;
		}

		Method[] methods = baseTypeMethodsCache.get(baseType);
		if (methods == null) {
			// 获取类上声明的方法和直接实现的接口的默认方法
			methods = ReflectionUtils.getDeclaredMethods(baseType);
			int cleared = 0;
			for (int i = 0; i < methods.length; i++) {
				if (Modifier.isPrivate(methods[i].getModifiers()) ||
						hasPlainJavaAnnotationsOnly(methods[i]) ||
						getDeclaredAnnotations(methods[i], false).length == 0) {
					methods[i] = null;
					cleared++;
				}
			}
			if (cleared == methods.length) {
				methods = NO_METHODS;
			}
			baseTypeMethodsCache.put(baseType, methods);
		}
		return methods;
	}

	/**
	 * rootMethod 是子类中的方法
	 * <p>candidateMethod 是接口或超类中的方法
	 * <p>判断 rootMethod 是否是 candidateMethod 方法的的覆写方法
	 * @param rootMethod
	 * @param candidateMethod
	 * @return
	 */
	private static boolean isOverride(Method rootMethod, Method candidateMethod) {
		return (!Modifier.isPrivate(candidateMethod.getModifiers()) &&
				candidateMethod.getName().equals(rootMethod.getName()) &&
				// 判断两个方法的参数的数量和每个参数的类型是否一致
				hasSameParameterTypes(rootMethod, candidateMethod));
	}

	/**
	 * 判断 rootMethod 和 candidateMethod 是否有相同的参数数量、参数类型或参数泛型类型
	 * @param rootMethod
	 * @param candidateMethod
	 * @return
	 */
	private static boolean hasSameParameterTypes(Method rootMethod, Method candidateMethod) {
		if (candidateMethod.getParameterCount() != rootMethod.getParameterCount()) {
			return false;
		}
		Class<?>[] rootParameterTypes = rootMethod.getParameterTypes();
		Class<?>[] candidateParameterTypes = candidateMethod.getParameterTypes();
		if (Arrays.equals(candidateParameterTypes, rootParameterTypes)) {
			return true;
		}
		return hasSameGenericTypeParameters(rootMethod, candidateMethod,
				rootParameterTypes);
	}

	/**
	 * 判断 rootMethod 和 candidateMethod 是否有相同的参数泛型类型
	 * @param rootMethod
	 * @param candidateMethod
	 * @param rootParameterTypes
	 * @return
	 */
	private static boolean hasSameGenericTypeParameters(
			Method rootMethod, Method candidateMethod, Class<?>[] rootParameterTypes) {

		Class<?> sourceDeclaringClass = rootMethod.getDeclaringClass();
		Class<?> candidateDeclaringClass = candidateMethod.getDeclaringClass();
		if (!candidateDeclaringClass.isAssignableFrom(sourceDeclaringClass)) {
			return false;
		}
		for (int i = 0; i < rootParameterTypes.length; i++) {
			Class<?> resolvedParameterType = ResolvableType.forMethodParameter(
					candidateMethod, i, sourceDeclaringClass).resolve();
			if (rootParameterTypes[i] != resolvedParameterType) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 获取方法 source 上的注解数组信息，并将注解数组信息传递给注解处理器，进行注解处理返回指定类型的结果
	 * @param context
	 * @param aggregateIndex
	 * @param source
	 * @param processor
	 * @return
	 * @param <C>
	 * @param <R>
	 */
	@Nullable
	private static <C, R> R processMethodAnnotations(C context, int aggregateIndex, Method source,
			AnnotationsProcessor<C, R> processor) {

		// 获取方法上的注解数组信息
		Annotation[] annotations = getDeclaredAnnotations(source, false);
		R result = processor.doWithAnnotations(context, aggregateIndex, source, annotations);
		if (result != null) {
			return result;
		}

		// 如果 source 是桥接方法，则获取其对应的被桥接方法(桥接方法的原始方法)，若 source 不是桥接方法，则返回当前方法(普通方法都可认为是被桥接方法)
		Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(source);
		if (bridgedMethod != source) {
			// 获取被桥接方法上的注解数组信息
			Annotation[] bridgedAnnotations = getDeclaredAnnotations(bridgedMethod, true);
			for (int i = 0; i < bridgedAnnotations.length; i++) {
				// 剔除桥接方法中已处理的注解 annotations，仅处理被桥接方法专有的注解
				if (ObjectUtils.containsElement(annotations, bridgedAnnotations[i])) {
					bridgedAnnotations[i] = null;
				}
			}
			return processor.doWithAnnotations(context, aggregateIndex, source, bridgedAnnotations);
		}
		return null;
	}

	/**
	 * 通过指定可被注解元素上的注解数组信息，并将注解数组信息传递给注解处理器，进行注解处理返回指定类型的结果
	 * @param context
	 * @param source
	 * @param processor
	 * @return
	 * @param <C>
	 * @param <R>
	 */
	@Nullable
	private static <C, R> R processElement(C context, AnnotatedElement source,
			AnnotationsProcessor<C, R> processor) {

		try {
			R result = processor.doWithAggregate(context, 0);
			return (result != null ? result : processor.doWithAnnotations(
				context, 0, source, getDeclaredAnnotations(source, false)));
		}
		catch (Throwable ex) {
			AnnotationUtils.handleIntrospectionFailure(source, ex);
		}
		return null;
	}

	/**
	 * 获取注解元素上声明的指定注解类型的注解信息
	 * @param source
	 * @param annotationType
	 * @return
	 * @param <A>
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	static <A extends Annotation> A getDeclaredAnnotation(AnnotatedElement source, Class<A> annotationType) {
		Annotation[] annotations = getDeclaredAnnotations(source, false);
		for (Annotation annotation : annotations) {
			if (annotation != null && annotationType == annotation.annotationType()) {
				return (A) annotation;
			}
		}
		return null;
	}

	/**
	 * 获取可被注解元素上声明的注解数组信息
	 * @param source
	 * @param defensive
	 * @return
	 */
	static Annotation[] getDeclaredAnnotations(AnnotatedElement source, boolean defensive) {
		boolean cached = false;
		// 获取已经处理过的元素上缓存的注解数组信息
		Annotation[] annotations = declaredAnnotationCache.get(source);
		if (annotations != null) {
			cached = true;
		}
		else {
			// 返回此元素（Class、 Method、 Constructor、 Field 等元素）上直接声明的注解，忽略继承的注解
			annotations = source.getDeclaredAnnotations();
			if (annotations.length != 0) {
				boolean allIgnored = true;
				for (int i = 0; i < annotations.length; i++) {
					Annotation annotation = annotations[i];
					// 如果注解是"java.lang"及其子包或"org.springframework.lang"及其子包中的注解类，或者注解的属性方法不能加载，则忽略该注解信息
					if (isIgnorable(annotation.annotationType()) ||
							!AttributeMethods.forAnnotationType(annotation.annotationType()).canLoad(annotation)) {
						annotations[i] = null;
					}
					else {
						allIgnored = false;
					}
				}
				annotations = (allIgnored ? NO_ANNOTATIONS : annotations);
				// 即缓存 Class、Method、Constructor、Field 等可注解的元素
				if (source instanceof Class || source instanceof Member) {
					// 缓存元素上的注解数组信息
					declaredAnnotationCache.put(source, annotations);
					cached = true;
				}
			}
		}
		// 若无注解信息，或注解无需存入缓存，且不以防御性方式获取，此时可直接返回反射到的原始的注解信息
		if (!defensive || annotations.length == 0 || !cached) {
			return annotations;
		}
		// 否则，返回注解数组的副本，避免缓存的注解信息返回后被修改，以造成干扰
		return annotations.clone();
	}

	private static boolean isIgnorable(Class<?> annotationType) {
		return AnnotationFilter.PLAIN.matches(annotationType);
	}

	/**
     * 判断可被注解元素上是否没有业务注解信息
	 * <p>1、可被注解元素只有普通注解
	 * <p>或2、使用直接搜索策略或可被注解元素不带层次机构，同时桥接方法返回 false 被任务是有业务注解信息的，其他可被注解元素上没有注解信息返回 true
	 * <p>或3、其他场景返回 false，认为可被注解元素上是有业务注解信息
     * @param source
     * @param searchStrategy
     * @param searchEnclosingClass
     * @return
     */
	static boolean isKnownEmpty(AnnotatedElement source, SearchStrategy searchStrategy,
			Predicate<Class<?>> searchEnclosingClass) {

		if (hasPlainJavaAnnotationsOnly(source)) {
			return true;
		}
		if (searchStrategy == SearchStrategy.DIRECT || isWithoutHierarchy(source, searchEnclosingClass)) {
			if (source instanceof Method method && method.isBridge()) {
				return false;
			}
			return getDeclaredAnnotations(source, false).length == 0;
		}
		return false;
	}

	/**
	 * 判断被注解对象是 Class 或所在的 Class 只有普通 Java 注解，表示声明的注解无业务意义。
	 * <p>实际逻辑是: Class 或被注解对象的所在的 Class 是 java. 的子包中的类或 Ordered 类，或对应类上的方法，则不用解析其注解
	 * @param annotatedElement
	 * @return
	 */
	static boolean hasPlainJavaAnnotationsOnly(@Nullable Object annotatedElement) {
		if (annotatedElement instanceof Class<?> clazz) {
			return hasPlainJavaAnnotationsOnly(clazz);
		}
		else if (annotatedElement instanceof Member member) {
			return hasPlainJavaAnnotationsOnly(member.getDeclaringClass());
		}
		else {
			return false;
		}
	}

	static boolean hasPlainJavaAnnotationsOnly(Class<?> type) {
		return (type.getName().startsWith("java.") || type == Ordered.class);
	}

	/**
	 * 判断可被注解元素是否没有层次结构
	 * <p>1、可被注解元素是 Object.class
	 * <p>或2、无实现接口，父类是 Object.class，且无封装类或不搜索封装类
	 * <p>或3、反射方法 Method 是私有方法，或 Method 声明的 Class 满足 1、2 中的规则
	 * <p>或4、其他场景默认返回 true，表示没有层次结构
	 * @param source
	 * @param searchEnclosingClass
	 * @return
	 */
	private static boolean isWithoutHierarchy(AnnotatedElement source, Predicate<Class<?>> searchEnclosingClass) {
		if (source == Object.class) {
			return true;
		}
		if (source instanceof Class<?> sourceClass) {
			boolean noSuperTypes = (sourceClass.getSuperclass() == Object.class &&
					sourceClass.getInterfaces().length == 0);
			return (searchEnclosingClass.test(sourceClass) ? noSuperTypes &&
					sourceClass.getEnclosingClass() == null : noSuperTypes);
		}
		if (source instanceof Method sourceMethod) {
			return (Modifier.isPrivate(sourceMethod.getModifiers()) ||
					isWithoutHierarchy(sourceMethod.getDeclaringClass(), searchEnclosingClass));
		}
		return true;
	}

	static void clearCache() {
		declaredAnnotationCache.clear();
		baseTypeMethodsCache.clear();
	}

}
