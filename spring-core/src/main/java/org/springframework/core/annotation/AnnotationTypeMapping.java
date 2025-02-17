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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.annotation.AnnotationTypeMapping.MirrorSets.MirrorSet;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Provides mapping information for a single annotation (or meta-annotation) in
 * the context of a root annotation type.
 *<p>在根注解类型上下文中提供单个注解（或元注解）的映射信息。
 *
 * <p>AnnotationTypeMapping 又被称为注解类型映射器，每一个注解类型会对应一个 AnnotationTypeMapping，根注解类型对应的 AnnotationTypeMapping
 * 称为根 AnnotationTypeMapping ，其他注解在根注解（或非根的元注解）上的元注解的注解类型 对应的 AnnotationTypeMapping 的源（source）
 * 属性指向根 AnnotationTypeMapping（或使用此元注解的注解的 AnnotationTypeMapping），根据此递归原则将形成一颗 AnnotationTypeMapping 树
 *
 * <p>而其中 @AliasFor 用于将根注解（入口注解）或源注解中的属性（别名属性）作为其元注解属性或源注解的其他属性（目标属性）的别名，
 * 即别名属性是当前注解中的属性，目标属性是元注解中的属性或当前注解中的其他属性，
 * 并通过层层的 @AliasFor 传递到最顶层元注解的属性，形成一颗别名关系树，与 AnnotationTypeMapping 树方向相反
 *
 * <p>使用 @AliasFor 注解的限制：1、@AliasFor 注解的属性若指定为其他注解类属性的别名，则该注解必须是当前注解的元注解，
 * 2、@AliasFor 注解的属性若指定为其他注解类（或未指定），则认为是当前注解的其他属性的别名，
 * 3、@AliasFor 注解的属性必须要有默认值
 * 4、同一注解中的两个属性互为彼此的别名，则默认值必须一致
 * 5、所有别名属性和目标属性的解析后的最终结果值需要相同，否则会报异常
 *
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Juergen Hoeller
 * @since 5.2
 * @see AnnotationTypeMappings
 */
final class AnnotationTypeMapping {

	private static final Log logger = LogFactory.getLog(AnnotationTypeMapping.class);

	private static final Predicate<? super Annotation> isBeanValidationConstraint = annotation ->
			annotation.annotationType().getName().equals("jakarta.validation.Constraint");

	/**
	 * Set used to track which convention-based annotation attribute overrides
	 * have already been checked. Each key is the combination of the fully
	 * qualified class name of a composed annotation and a meta-annotation
	 * that it is either present or meta-present on the composed annotation,
	 * separated by a dash.
	 * @since 6.0
	 * @see #addConventionMappings()
	 */
	private static final Set<String> conventionBasedOverrideCheckCache = ConcurrentHashMap.newKeySet();

	private static final MirrorSet[] EMPTY_MIRROR_SETS = new MirrorSet[0];

	private static final int[] EMPTY_INT_ARRAY = new int[0];


	// AnnotationTypeMapping 树中该节点的父节点（或称为源节点），根 AnnotationTypeMapping 对应的是 null
	@Nullable
	private final AnnotationTypeMapping source;

	// AnnotationTypeMapping 树的根
	private final AnnotationTypeMapping root;

	// 该节点到根节点的距离
	private final int distance;

	// 注解类型
	private final Class<? extends Annotation> annotationType;

	// 该注解类型到根注解类型中间连接的所有注解类型的列表
	private final List<Class<? extends Annotation>> metaTypes;

	// 该注解类型对应的注解实例，根注解类型对应的是 null
	@Nullable
	private final Annotation annotation;

	// 当前注解类型的属性方法/属性
	private final AttributeMethods attributes;

	private final MirrorSets mirrorSets;

	// 别名映射关系，用于存储当前注解类型属性方法索引与根注解类型属性方法索引之间的别名映射关系
	private final int[] aliasMappings;

	// 命名约定映射关系，用于存储当前注解类型属性方法与根注解类型属性方法之间的命名约定映射关系（通过属性名称做别名映射，而不是显示的@AliasFor注解）
	// 若某属性同时满足 aliasMappings 和 conventionMappings 的映射关系，则以 aliasMappings 中的映射关系为准
	private final int[] conventionMappings;

	// 注解值映射关系，用于存储当前注解类型属性方法与数据源注解类型（可能是中间间隔多层）属性方法之间的注解值映射关系，
	// 当前注解的属性索引位，对应 annotationValueSource 中的 AnnotationTypeMapping 的属性索引位
	private final int[] annotationValueMappings;

	// 当前注解属性值来源的数据源 AnnotationTypeMapping (可能中间间隔多层)，即该属性值，能从数据源注解的 AnnotationTypeMapping 中获取到属性值
	// 主要用于属性值不能从根注解属性对应获取，即从当前注解或其他元注解中获取值的场景
	private final AnnotationTypeMapping[] annotationValueSource;

	// 此时 key 是目标属性的反射方法， value 是目标属性在当前注解中的所有别名属性的反射方法的列表，
	private final Map<Method, List<Method>> aliasedBy;

	// 是否可合成的
	private final boolean synthesizable;

	// 会存储当前 AnnotationTypeMapping 树中所有使用 @AliasFor 声明关联的所有目标属性和所有别名属性的反射方法
	private final Set<Method> claimedAliases = new HashSet<>();


	AnnotationTypeMapping(@Nullable AnnotationTypeMapping source, Class<? extends Annotation> annotationType,
			@Nullable Annotation annotation, Set<Class<? extends Annotation>> visitedAnnotationTypes) {

		this.source = source;
		this.root = (source != null ? source.getRoot() : this);
		this.distance = (source == null ? 0 : source.getDistance() + 1);
		this.annotationType = annotationType;
		this.metaTypes = merge(
				source != null ? source.getMetaTypes() : null,
				annotationType);
		this.annotation = annotation;
		this.attributes = AttributeMethods.forAnnotationType(annotationType);
		this.mirrorSets = new MirrorSets();
		// 生成一个指定长度的 int[] 数组，并将所有元素初始化值为 -1
		this.aliasMappings = filledIntArray(this.attributes.size());
		this.conventionMappings = filledIntArray(this.attributes.size());
		this.annotationValueMappings = filledIntArray(this.attributes.size());
		this.annotationValueSource = new AnnotationTypeMapping[this.attributes.size()];
		// 解析当前注解类型属性中被 @AliasFor 注解的属性方法，并生成目标属性作为key，目标属性在当前注解中的所有别名属性的反射方法的列表作为value的映射关系
		this.aliasedBy = resolveAliasedForTargets();
		// 从当前注解递归到根注解的逐层别名处理
		processAliases();
		addConventionMappings();
		addConventionAnnotationValues();
		this.synthesizable = computeSynthesizableFlag(visitedAnnotationTypes);
	}


	private static <T> List<T> merge(@Nullable List<T> existing, T element) {
		if (existing == null) {
			return Collections.singletonList(element);
		}
		List<T> merged = new ArrayList<>(existing.size() + 1);
		merged.addAll(existing);
		merged.add(element);
		return Collections.unmodifiableList(merged);
	}

	private Map<Method, List<Method>> resolveAliasedForTargets() {
		Map<Method, List<Method>> aliasedBy = new HashMap<>();
		for (int i = 0; i < this.attributes.size(); i++) {
			Method attribute = this.attributes.get(i);
			// 获取注解属性方法上的 AliasFor 注解
			AliasFor aliasFor = AnnotationsScanner.getDeclaredAnnotation(attribute, AliasFor.class);
			if (aliasFor != null) {
				// 解析属性方法的 AliasFor 注解，指定当前属性方法将作为指定注解属性方法的别名，并返回指定注解的属性方法的Method对象
				Method target = resolveAliasTarget(attribute, aliasFor);
				// 存储从元注解的属性（目标属性）到当前注解属性（别名属性）的映射关系
				aliasedBy.computeIfAbsent(target, key -> new ArrayList<>()).add(attribute);
			}
		}
		return Collections.unmodifiableMap(aliasedBy);
	}

	private Method resolveAliasTarget(Method attribute, AliasFor aliasFor) {
		return resolveAliasTarget(attribute, aliasFor, true);
	}

	private Method resolveAliasTarget(Method attribute, AliasFor aliasFor, boolean checkAliasPair) {
		// AliasFor 注解的 value 和 attribute 属性不能同时存在
		if (StringUtils.hasText(aliasFor.value()) && StringUtils.hasText(aliasFor.attribute())) {
			throw new AnnotationConfigurationException(String.format(
					"In @AliasFor declared on %s, attribute 'attribute' and its alias 'value' " +
					"are present with values of '%s' and '%s', but only one is permitted.",
					AttributeMethods.describe(attribute), aliasFor.attribute(),
					aliasFor.value()));
		}
		Class<? extends Annotation> targetAnnotation = aliasFor.annotation();
		// 如果 targetAnnotation 是 Annotation.class（注解未指定 annotation 属性时），则将其替换为当前注解类型
		if (targetAnnotation == Annotation.class) {
			targetAnnotation = this.annotationType;
		}
		String targetAttributeName = aliasFor.attribute();
		if (!StringUtils.hasLength(targetAttributeName)) {
			targetAttributeName = aliasFor.value();
		}
		// AliasFor 注解的 value 和 attribute 属性都未指定时，使用当前属性方法的名称
		if (!StringUtils.hasLength(targetAttributeName)) {
			targetAttributeName = attribute.getName();
		}
		Method target = AttributeMethods.forAnnotationType(targetAnnotation).get(targetAttributeName);
		// 如果 target 是 null，则说明 targetAnnotation 上不存在 targetAttributeName 属性，将会抛出异常
		if (target == null) {
			if (targetAnnotation == this.annotationType) {
				throw new AnnotationConfigurationException(String.format(
						"@AliasFor declaration on %s declares an alias for '%s' which is not present.",
						AttributeMethods.describe(attribute), targetAttributeName));
			}
			throw new AnnotationConfigurationException(String.format(
					"%s is declared as an @AliasFor nonexistent %s.",
					StringUtils.capitalize(AttributeMethods.describe(attribute)),
					AttributeMethods.describe(targetAnnotation, targetAttributeName)));
		}
		// 如果 target 是当前属性方法，则说明 targetAnnotation 上存在 targetAttributeName 属性且别名是当前属性方法，将会抛出异常
		if (target.equals(attribute)) {
			throw new AnnotationConfigurationException(String.format(
					"@AliasFor declaration on %s points to itself. " +
					"Specify 'annotation' to point to a same-named attribute on a meta-annotation.",
					AttributeMethods.describe(attribute)));
		}
		// 如果 target 属性方法的返回值类型和 attribute 属性方法的返回值类型不匹配，将会抛出异常
		if (!isCompatibleReturnType(attribute.getReturnType(), target.getReturnType())) {
			throw new AnnotationConfigurationException(String.format(
					"Misconfigured aliases: %s and %s must declare the same return type.",
					AttributeMethods.describe(attribute),
					AttributeMethods.describe(target)));
		}
		// target 和 attribute 是同一个注解中的两个属性，且都有 @AliasFor 注解时，若 checkAliasPair 为 true，则必须互为别名，否则将会报错
		if (isAliasPair(target) && checkAliasPair) {
			AliasFor targetAliasFor = target.getAnnotation(AliasFor.class);
			if (targetAliasFor != null) {
				Method mirror = resolveAliasTarget(target, targetAliasFor, false);
				if (!mirror.equals(attribute)) {
					throw new AnnotationConfigurationException(String.format(
							"%s must be declared as an @AliasFor %s, not %s.",
							StringUtils.capitalize(AttributeMethods.describe(target)),
							AttributeMethods.describe(attribute), AttributeMethods.describe(mirror)));
				}
			}
		}
		return target;
	}

	private boolean isAliasPair(Method target) {
		return (this.annotationType == target.getDeclaringClass());
	}

	private boolean isCompatibleReturnType(Class<?> attributeType, Class<?> targetType) {
		return (attributeType == targetType || attributeType == targetType.componentType());
	}

	private void processAliases() {
		List<Method> aliases = new ArrayList<>();
		// 逐个注解属性处理
		for (int i = 0; i < this.attributes.size(); i++) {
			aliases.clear();
			// 将目标属性放入别名列表中，用于查询出他关联的别名属性
			aliases.add(this.attributes.get(i));
			// 获取当前属性方法作为目标属性的所有别名属性方法，并递归到根注解的所有别名属性方法放入 aliases 中
			collectAliases(aliases);
			// 如果 aliases 中存在多个元素，则说明存在别名，需要处理
			if (aliases.size() > 1) {
				processAliases(i, aliases);
			}
		}
	}

	private void collectAliases(List<Method> aliases) {
		AnnotationTypeMapping mapping = this;
		// 循环到根 AnnotationTypeMapping
		while (mapping != null) {
			int size = aliases.size();
			for (int j = 0; j < size; j++) {
				// 获取当前属性方法作为目标属性的所有别名属性方法，并通过 while 递归，可获取到对应的源注解的所有别名属性方法放入 aliases 中
				List<Method> additional = mapping.aliasedBy.get(aliases.get(j));
				if (additional != null) {
					aliases.addAll(additional);
				}
			}
			mapping = mapping.source;
		}
	}

	private void processAliases(int attributeIndex, List<Method> aliases) {
		// 获取根 AnnotationTypeMapping 的注解类型中属性，属性存在在这些别名属性列表中，则返回其对属性的索引，否则返回 -2
		int rootAttributeIndex = getFirstRootAttributeIndex(aliases);
		AnnotationTypeMapping mapping = this;
		while (mapping != null) {
			// 根注解中有属性方法共用这部分别名属性方法，且当前的 mapping 不是根注解的 mapping
			if (rootAttributeIndex != -1 && mapping != this.root) {
				for (int i = 0; i < mapping.attributes.size(); i++) {
					if (aliases.contains(mapping.attributes.get(i))) {
						// 调整当前注解及其关联的源注解（递归）的属性方法的索引位映射到根注解属性方法的索引位
						mapping.aliasMappings[i] = rootAttributeIndex;
					}
				}
			}
			mapping.mirrorSets.updateFrom(aliases);
			mapping.claimedAliases.addAll(aliases);
			// 非根注解，根注解没有实际的注解对象信息
			if (mapping.annotation != null) {
				// 获取当前非根注解的 mapping 的一组镜像属性，实际使用注解信息中的哪个具体别名属性获取值，
				// 所有同质的别名属性统一调用特定一个别名属性获取值，这部分别名属性镜像到相同的属性索引位
				int[] resolvedMirrors = mapping.mirrorSets.resolve(null,
						mapping.annotation, AnnotationUtils::invokeAnnotationMethod);
				for (int i = 0; i < mapping.attributes.size(); i++) {
					if (aliases.contains(mapping.attributes.get(i))) {
						// 存储当前注解的属性使用的数据源 AnnotationTypeMapping 对应的注解中的目标属性的索引位
						this.annotationValueMappings[attributeIndex] = resolvedMirrors[i];
						// 更新当前注解的属性取值使用的数据源 AnnotationTypeMapping ，最终会存储一个最接近根注解的 AnnotationTypeMapping
						this.annotationValueSource[attributeIndex] = mapping;
					}
				}
			}
			mapping = mapping.source;
		}
	}

	private int getFirstRootAttributeIndex(Collection<Method> aliases) {
		AttributeMethods rootAttributes = this.root.getAttributes();
		for (int i = 0; i < rootAttributes.size(); i++) {
			if (aliases.contains(rootAttributes.get(i))) {
				return i;
			}
		}
		return -1;
	}

	private void addConventionMappings() {
		if (this.distance == 0) {
			return;
		}
		AttributeMethods rootAttributes = this.root.getAttributes();
		int[] mappings = this.conventionMappings;
		Set<String> conventionMappedAttributes = new HashSet<>();
		for (int i = 0; i < mappings.length; i++) {
			String name = this.attributes.get(i).getName();
			int mapped = rootAttributes.indexOf(name);
			if (!MergedAnnotation.VALUE.equals(name) && mapped != -1 && !isExplicitAttributeOverride(name)) {
				conventionMappedAttributes.add(name);
				mappings[i] = mapped;
				MirrorSet mirrors = getMirrorSets().getAssigned(i);
				if (mirrors != null) {
					for (int j = 0; j < mirrors.size(); j++) {
						mappings[mirrors.getAttributeIndex(j)] = mapped;
					}
				}
			}
		}
		String rootAnnotationTypeName = this.root.annotationType.getName();
		String cacheKey = rootAnnotationTypeName + '-' + this.annotationType.getName();
		// We want to avoid duplicate log warnings as much as possible, without full synchronization,
		// and we intentionally invoke add() before checking if any convention-based overrides were
		// actually encountered in order to ensure that we add a "tracked" entry for the current cache
		// key in any case.
		// In addition, we do NOT want to log warnings for custom Java Bean Validation constraint
		// annotations that are meta-annotated with other constraint annotations -- for example,
		// @org.hibernate.validator.constraints.URL which overrides attributes in
		// @jakarta.validation.constraints.Pattern.
		if (conventionBasedOverrideCheckCache.add(cacheKey) && !conventionMappedAttributes.isEmpty() &&
				Arrays.stream(this.annotationType.getAnnotations()).noneMatch(isBeanValidationConstraint) &&
				logger.isWarnEnabled()) {
			logger.warn("""
					Support for convention-based annotation attribute overrides is deprecated \
					and will be removed in Spring Framework 7.0. Please annotate the following \
					attributes in @%s with appropriate @AliasFor declarations: %s"""
						.formatted(rootAnnotationTypeName, conventionMappedAttributes));
		}
	}

	/**
	 * Determine if the given annotation attribute in the {@linkplain #getRoot()
	 * root annotation} is an explicit annotation attribute override for an
	 * attribute in a meta-annotation, explicit in the sense that the override
	 * is declared via {@link AliasFor @AliasFor}.
	 * <p>If the named attribute does not exist in the root annotation, this
	 * method returns {@code false}.
	 * @param name the name of the annotation attribute to check
	 * @since 6.0
	 */
	private boolean isExplicitAttributeOverride(String name) {
		Method attribute = this.root.getAttributes().get(name);
		if (attribute != null) {
			AliasFor aliasFor = AnnotationsScanner.getDeclaredAnnotation(attribute, AliasFor.class);
			return ((aliasFor != null) &&
					(aliasFor.annotation() != Annotation.class) &&
					(aliasFor.annotation() != this.root.annotationType));
		}
		return false;
	}

	private void addConventionAnnotationValues() {
		for (int i = 0; i < this.attributes.size(); i++) {
			Method attribute = this.attributes.get(i);
			boolean isValueAttribute = MergedAnnotation.VALUE.equals(attribute.getName());
			AnnotationTypeMapping mapping = this;
			// 名称约定映射，仅适用于非根 AnnotationTypeMapping
			while (mapping != null && mapping.distance > 0) {
				int mapped = mapping.getAttributes().indexOf(attribute.getName());
				// mapping 对应的注解中的名称匹配的属性，比原有的 annotationValueSource 更接近根注解时，使用改 mapping 作为注解属性值解析的数据源
				if (mapped != -1 && isBetterConventionAnnotationValue(i, isValueAttribute, mapping)) {
					this.annotationValueMappings[i] = mapped;
					this.annotationValueSource[i] = mapping;
				}
				mapping = mapping.source;
			}
		}
	}

	private boolean isBetterConventionAnnotationValue(int index, boolean isValueAttribute,
			AnnotationTypeMapping mapping) {

		if (this.annotationValueMappings[index] == -1) {
			return true;
		}
		int existingDistance = this.annotationValueSource[index].distance;
		return !isValueAttribute && existingDistance > mapping.distance;
	}

	@SuppressWarnings("unchecked")
	private boolean computeSynthesizableFlag(Set<Class<? extends Annotation>> visitedAnnotationTypes) {
		// Track that we have visited the current annotation type.
		visitedAnnotationTypes.add(this.annotationType);

		// Uses @AliasFor for local aliases?
		for (int index : this.aliasMappings) {
			if (index != -1) {
				return true;
			}
		}

		// Uses @AliasFor for attribute overrides in meta-annotations?
		if (!this.aliasedBy.isEmpty()) {
			return true;
		}

		// Uses convention-based attribute overrides in meta-annotations?
		for (int index : this.conventionMappings) {
			if (index != -1) {
				return true;
			}
		}

		// Has nested annotations or arrays of annotations that are synthesizable?
		if (getAttributes().hasNestedAnnotation()) {
			AttributeMethods attributeMethods = getAttributes();
			for (int i = 0; i < attributeMethods.size(); i++) {
				Method method = attributeMethods.get(i);
				Class<?> type = method.getReturnType();
				if (type.isAnnotation() || (type.isArray() && type.componentType().isAnnotation())) {
					Class<? extends Annotation> annotationType =
							(Class<? extends Annotation>) (type.isAnnotation() ? type : type.componentType());
					// Ensure we have not yet visited the current nested annotation type, in order
					// to avoid infinite recursion for JVM languages other than Java that support
					// recursive annotation definitions.
					if (visitedAnnotationTypes.add(annotationType)) {
						AnnotationTypeMapping mapping =
								AnnotationTypeMappings.forAnnotationType(annotationType, visitedAnnotationTypes).get(0);
						if (mapping.isSynthesizable()) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	/**
	 * Method called after all mappings have been set. At this point no further
	 * lookups from child mappings will occur.
	 */
	void afterAllMappingsSet() {
		validateAllAliasesClaimed();
		for (int i = 0; i < this.mirrorSets.size(); i++) {
			validateMirrorSet(this.mirrorSets.get(i));
		}
		this.claimedAliases.clear();
	}

	private void validateAllAliasesClaimed() {
		for (int i = 0; i < this.attributes.size(); i++) {
			Method attribute = this.attributes.get(i);
			AliasFor aliasFor = AnnotationsScanner.getDeclaredAnnotation(attribute, AliasFor.class);
			if (aliasFor != null && !this.claimedAliases.contains(attribute)) {
				Method target = resolveAliasTarget(attribute, aliasFor);
				throw new AnnotationConfigurationException(String.format(
						"@AliasFor declaration on %s declares an alias for %s which is not meta-present.",
						AttributeMethods.describe(attribute), AttributeMethods.describe(target)));
			}
		}
	}

	private void validateMirrorSet(MirrorSet mirrorSet) {
		Method firstAttribute = mirrorSet.get(0);
		Object firstDefaultValue = firstAttribute.getDefaultValue();
		for (int i = 1; i <= mirrorSet.size() - 1; i++) {
			Method mirrorAttribute = mirrorSet.get(i);
			Object mirrorDefaultValue = mirrorAttribute.getDefaultValue();
			if (firstDefaultValue == null || mirrorDefaultValue == null) {
				throw new AnnotationConfigurationException(String.format(
						"Misconfigured aliases: %s and %s must declare default values.",
						AttributeMethods.describe(firstAttribute), AttributeMethods.describe(mirrorAttribute)));
			}
			if (!ObjectUtils.nullSafeEquals(firstDefaultValue, mirrorDefaultValue)) {
				throw new AnnotationConfigurationException(String.format(
						"Misconfigured aliases: %s and %s must declare the same default value.",
						AttributeMethods.describe(firstAttribute), AttributeMethods.describe(mirrorAttribute)));
			}
		}
	}

	/**
	 * Get the root mapping.
	 * @return the root mapping
	 */
	AnnotationTypeMapping getRoot() {
		return this.root;
	}

	/**
	 * Get the source of the mapping or {@code null}.
	 * @return the source of the mapping
	 */
	@Nullable
	AnnotationTypeMapping getSource() {
		return this.source;
	}

	/**
	 * Get the distance of this mapping.
	 * @return the distance of the mapping
	 */
	int getDistance() {
		return this.distance;
	}

	/**
	 * Get the type of the mapped annotation.
	 * @return the annotation type
	 */
	Class<? extends Annotation> getAnnotationType() {
		return this.annotationType;
	}

	List<Class<? extends Annotation>> getMetaTypes() {
		return this.metaTypes;
	}

	/**
	 * Get the source annotation for this mapping. This will be the
	 * meta-annotation, or {@code null} if this is the root mapping.
	 * @return the source annotation of the mapping
	 */
	@Nullable
	Annotation getAnnotation() {
		return this.annotation;
	}

	/**
	 * Get the annotation attributes for the mapping annotation type.
	 * @return the attribute methods
	 */
	AttributeMethods getAttributes() {
		return this.attributes;
	}

	/**
	 * Get the related index of an alias mapped attribute, or {@code -1} if
	 * there is no mapping. The resulting value is the index of the attribute on
	 * the root annotation that can be invoked in order to obtain the actual
	 * value.
	 * @param attributeIndex the attribute index of the source attribute
	 * @return the mapped attribute index or {@code -1}
	 */
	int getAliasMapping(int attributeIndex) {
		return this.aliasMappings[attributeIndex];
	}

	/**
	 * Get the related index of a convention mapped attribute, or {@code -1}
	 * if there is no mapping. The resulting value is the index of the attribute
	 * on the root annotation that can be invoked in order to obtain the actual
	 * value.
	 * @param attributeIndex the attribute index of the source attribute
	 * @return the mapped attribute index or {@code -1}
	 */
	int getConventionMapping(int attributeIndex) {
		return this.conventionMappings[attributeIndex];
	}

	/**
	 * Get a mapped attribute value from the most suitable
	 * {@link #getAnnotation() meta-annotation}.
	 * <p>The resulting value is obtained from the closest meta-annotation,
	 * taking into consideration both convention and alias based mapping rules.
	 * For root mappings, this method will always return {@code null}.
	 * <p>从最合适的元注释中获取映射的属性值。结果值是从最近的元注释中获取的，同时考虑了命名约定映射和基于别名的映射规则。对于根映射，此方法将始终返回 null。
	 * @param attributeIndex the attribute index of the source attribute
	 * @param metaAnnotationsOnly if only meta annotations should be considered.
	 * If this parameter is {@code false} then aliases within the annotation will
	 * also be considered.
	 * @return the mapped annotation value, or {@code null}
	 */
	@Nullable
	Object getMappedAnnotationValue(int attributeIndex, boolean metaAnnotationsOnly) {
		int mappedIndex = this.annotationValueMappings[attributeIndex];
		if (mappedIndex == -1) {
			return null;
		}
		AnnotationTypeMapping source = this.annotationValueSource[attributeIndex];
		if (source == this && metaAnnotationsOnly) {
			return null;
		}
		return AnnotationUtils.invokeAnnotationMethod(source.attributes.get(mappedIndex), source.annotation);
	}

	/**
	 * Determine if the specified value is equivalent to the default value of the
	 * attribute at the given index.
	 * @param attributeIndex the attribute index of the source attribute
	 * @param value the value to check
	 * @param valueExtractor the value extractor used to extract values from any
	 * nested annotations
	 * @return {@code true} if the value is equivalent to the default value
	 */
	boolean isEquivalentToDefaultValue(int attributeIndex, Object value, ValueExtractor valueExtractor) {
		Method attribute = this.attributes.get(attributeIndex);
		return isEquivalentToDefaultValue(attribute, value, valueExtractor);
	}

	/**
	 * Get the mirror sets for this type mapping.
	 * @return the attribute mirror sets
	 */
	MirrorSets getMirrorSets() {
		return this.mirrorSets;
	}

	/**
	 * Determine if the mapped annotation is <em>synthesizable</em>.
	 * <p>Consult the documentation for {@link MergedAnnotation#synthesize()}
	 * for an explanation of what is considered synthesizable.
	 * @return {@code true} if the mapped annotation is synthesizable
	 * @since 5.2.6
	 */
	boolean isSynthesizable() {
		return this.synthesizable;
	}


	private static int[] filledIntArray(int size) {
		if (size == 0) {
			return EMPTY_INT_ARRAY;
		}
		int[] array = new int[size];
		Arrays.fill(array, -1);
		return array;
	}

	private static boolean isEquivalentToDefaultValue(Method attribute, Object value,
			ValueExtractor valueExtractor) {

		return areEquivalent(attribute.getDefaultValue(), value, valueExtractor);
	}

	private static boolean areEquivalent(@Nullable Object value, @Nullable Object extractedValue,
			ValueExtractor valueExtractor) {

		if (ObjectUtils.nullSafeEquals(value, extractedValue)) {
			return true;
		}
		if (value instanceof Class<?> clazz && extractedValue instanceof String string) {
			return areEquivalent(clazz, string);
		}
		if (value instanceof Class<?>[] classes && extractedValue instanceof String[] strings) {
			return areEquivalent(classes, strings);
		}
		if (value instanceof Annotation annotation) {
			return areEquivalent(annotation, extractedValue, valueExtractor);
		}
		return false;
	}

	private static boolean areEquivalent(Class<?>[] value, String[] extractedValue) {
		if (value.length != extractedValue.length) {
			return false;
		}
		for (int i = 0; i < value.length; i++) {
			if (!areEquivalent(value[i], extractedValue[i])) {
				return false;
			}
		}
		return true;
	}

	private static boolean areEquivalent(Class<?> value, String extractedValue) {
		return value.getName().equals(extractedValue);
	}

	private static boolean areEquivalent(Annotation annotation, @Nullable Object extractedValue,
			ValueExtractor valueExtractor) {

		AttributeMethods attributes = AttributeMethods.forAnnotationType(annotation.annotationType());
		for (int i = 0; i < attributes.size(); i++) {
			Method attribute = attributes.get(i);
			Object value1 = AnnotationUtils.invokeAnnotationMethod(attribute, annotation);
			Object value2;
			if (extractedValue instanceof TypeMappedAnnotation<?> typeMappedAnnotation) {
				value2 = typeMappedAnnotation.getValue(attribute.getName()).orElse(null);
			}
			else {
				value2 = valueExtractor.extract(attribute, extractedValue);
			}
			if (!areEquivalent(value1, value2, valueExtractor)) {
				return false;
			}
		}
		return true;
	}


	/**
	 * A collection of {@link MirrorSet} instances that provides details of all
	 * defined mirrors.
	 * <p>MirrorSet 的实例集合，提供所有已定义镜像的详细信息
	 */
	class MirrorSets {

		// assigned 中剔除空值后去重的数组，即有几组属性共用别名属性和目标属性，就有几个MirrorSet
		private MirrorSet[] mirrorSets;

		// 仅注解的多个属性（单个属性就不需要此方式了）共用目标属性时，目标属性一致的属性变为一组，对应的属性的索引位处（如 assigned[0]），将赋值同一个 MirrorSet 对象，
		// 表示它们是同一组镜像；若有多组将会有多个MirrorSet 对象
		private final MirrorSet[] assigned;

		MirrorSets() {
			this.assigned = attributes.size() > 0 ? new MirrorSet[attributes.size()] : EMPTY_MIRROR_SETS;
			this.mirrorSets = EMPTY_MIRROR_SETS;
		}

		// 基于当前注解的多个别名属性（2个及以上，包括目标属性），创建 MirrorSet 记录一组镜像属性
		void updateFrom(Collection<Method> aliases) {
			MirrorSet mirrorSet = null;
			int size = 0;
			int last = -1;
			for (int i = 0; i < attributes.size(); i++) {
				Method attribute = attributes.get(i);
				// 判断当前属性是否是目标属性，或作为别名属性（同一个注解中的别名）
				if (aliases.contains(attribute)) {
					size++;
					// 当前注解有两个以上的属性共用相同的别名属性或作为相同目标属性的别名属性时，则创建一个新的MirrorSet，这部分目标索引位都存放相同的 MirrorSet
					if (size > 1) {
						if (mirrorSet == null) {
							mirrorSet = new MirrorSet();
							this.assigned[last] = mirrorSet;
						}
						this.assigned[i] = mirrorSet;
					}
					last = i;
				}
			}
			if (mirrorSet != null) {
				mirrorSet.update();
				Set<MirrorSet> unique = new LinkedHashSet<>(Arrays.asList(this.assigned));
				unique.remove(null);
				this.mirrorSets = unique.toArray(EMPTY_MIRROR_SETS);
			}
		}

		int size() {
			return this.mirrorSets.length;
		}

		MirrorSet get(int index) {
			return this.mirrorSets[index];
		}

		@Nullable
		MirrorSet getAssigned(int attributeIndex) {
			return this.assigned[attributeIndex];
		}

		// 解析出镜像属性对应取值使用的属性方法的索引位
		int[] resolve(@Nullable Object source, @Nullable Object annotation, ValueExtractor valueExtractor) {
			if (attributes.size() == 0) {
				return EMPTY_INT_ARRAY;
			}
			int[] result = new int[attributes.size()];
			// 每个属性方法默认取值使用的镜像方法是自己
			for (int i = 0; i < result.length; i++) {
				result[i] = i;
			}
			for (int i = 0; i < size(); i++) {
				MirrorSet mirrorSet = get(i);
				// 确定当前注解中针对一组别名属性最终取值将使用哪个别名属性，返回对应别名属性的索引位作为镜像
				int resolved = mirrorSet.resolve(source, annotation, valueExtractor);
				for (int j = 0; j < mirrorSet.size; j++) {
					// 一组别名属性使用同一个属性取值，更新镜像的索引位
					result[mirrorSet.indexes[j]] = resolved;
				}
			}
			return result;
		}


		/**
		 * A single set of mirror attributes.
		 * <p>一组镜像属性。即当前注解中的共用同一个目标属性的一组别名属性，互为镜像属性，会组成一个 MirrorSet，多组将会有多个MirrorSet
		 */
		class MirrorSet {

			// 当前 MirrorSet 中共用同一个目标属性的别名属性个数
			private int size;

			// 当前 MirrorSet 在 MirrorSets.this.assigned 中的索引位（也是目标属性在注解中的索引位），有几个不同目标属性，就有几个索引位
			private final int[] indexes = new int[attributes.size()];

			void update() {
				this.size = 0;
				Arrays.fill(this.indexes, -1);
				for (int i = 0; i < MirrorSets.this.assigned.length; i++) {
					if (MirrorSets.this.assigned[i] == this) {
						this.indexes[this.size] = i;
						this.size++;
					}
				}
			}

			// 从镜像属性（一组别名属性）中解析出最终取值将使用哪个别名属性，获取未赋值的别名属性的访问转移到调用有赋值的别名属性的索引位
			<A> int resolve(@Nullable Object source, @Nullable A annotation, ValueExtractor valueExtractor) {
				int result = -1;
				Object lastValue = null;
				for (int i = 0; i < this.size; i++) {
					Method attribute = attributes.get(this.indexes[i]);
					Object value = valueExtractor.extract(attribute, annotation);
					// 如果注解中存在默认值，则判断当前属性值是否与默认值相等
					boolean isDefaultValue = (value == null ||
							isEquivalentToDefaultValue(attribute, value, valueExtractor));
					if (isDefaultValue || ObjectUtils.nullSafeEquals(lastValue, value)) {
						if (result == -1) {
							result = this.indexes[i];
						}
						continue;
					}
					if (lastValue != null && !ObjectUtils.nullSafeEquals(lastValue, value)) {
						String on = (source != null) ? " declared on " + source : "";
						throw new AnnotationConfigurationException(String.format(
								"Different @AliasFor mirror values for annotation [%s]%s; attribute '%s' " +
								"and its alias '%s' are declared with values of [%s] and [%s].",
								getAnnotationType().getName(), on,
								attributes.get(result).getName(),
								attribute.getName(),
								ObjectUtils.nullSafeToString(lastValue),
								ObjectUtils.nullSafeToString(value)));
					}
					result = this.indexes[i];
					lastValue = value;
				}
				return result;
			}

			int size() {
				return this.size;
			}

			Method get(int index) {
				int attributeIndex = this.indexes[index];
				return attributes.get(attributeIndex);
			}

			int getAttributeIndex(int index) {
				return this.indexes[index];
			}
		}
	}

}
