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

package org.springframework.core;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;

/**
 * Helper for resolving synthetic {@link Method#isBridge bridge Methods} to the
 * {@link Method} being bridged.
 *
 * <p>Given a synthetic {@link Method#isBridge bridge Method} returns the {@link Method}
 * being bridged. A bridge method may be created by the compiler when extending a
 * parameterized type whose methods have parameterized arguments. During runtime
 * invocation the bridge {@link Method} may be invoked and/or used via reflection.
 * When attempting to locate annotations on {@link Method Methods}, it is wise to check
 * for bridge {@link Method Methods} as appropriate and find the bridged {@link Method}.
 *
 * <p>See <a href="https://java.sun.com/docs/books/jls/third_edition/html/expressions.html#15.12.4.5">
 * The Java Language Specification</a> for more details on the use of bridge methods.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 2.0
 */
public final class BridgeMethodResolver {

	private static final Map<Object, Method> cache = new ConcurrentReferenceHashMap<>();

	private BridgeMethodResolver() {
	}


	/**
	 * Find the local original method for the supplied {@link Method bridge Method}.
	 * <p>It is safe to call this method passing in a non-bridge {@link Method} instance.
	 * In such a case, the supplied {@link Method} instance is returned directly to the caller.
	 * Callers are <strong>not</strong> required to check for bridging before calling this method.
	 * <p>查找提供的桥接方法的本地原始方法。
	 * <p>传入非桥接方法实例来调用此方法是安全的。在这种情况下，提供的方法实例将直接返回给调用者。调用者在调用此方法之前无需检查桥接。>
	 * @param bridgeMethod the method to introspect against its declaring class
	 * @return the original method (either the bridged method or the passed-in method
	 * if no more specific one could be found)
	 * @see #getMostSpecificMethod(Method, Class)
	 */
	public static Method findBridgedMethod(Method bridgeMethod) {
		return resolveBridgeMethod(bridgeMethod, bridgeMethod.getDeclaringClass());
	}

	/**
	 * Determine the most specific method for the supplied {@link Method bridge Method}
	 * in the given class hierarchy, even if not available on the local declaring class.
	 * <p>This is effectively a combination of {@link ClassUtils#getMostSpecificMethod}
	 * and {@link #findBridgedMethod}, resolving the original method even if no bridge
	 * method has been generated at the same class hierarchy level (a known difference
	 * between the Eclipse compiler and regular javac).
	 * @param bridgeMethod the method to introspect against the given target class
	 * @param targetClass the target class to find the most specific method on
	 * @return the most specific method corresponding to the given bridge method
	 * (can be the original method if no more specific one could be found)
	 * @since 6.1.3
	 * @see #findBridgedMethod
	 * @see org.springframework.util.ClassUtils#getMostSpecificMethod
	 */
	public static Method getMostSpecificMethod(Method bridgeMethod, @Nullable Class<?> targetClass) {
		if (targetClass != null &&
				!ClassUtils.getUserClass(bridgeMethod.getDeclaringClass()).isAssignableFrom(targetClass) &&
				!Proxy.isProxyClass(bridgeMethod.getDeclaringClass())) {
			// From a different class hierarchy, and not a JDK or CGLIB proxy either -> return as-is.
			return bridgeMethod;
		}

		Method specificMethod = ClassUtils.getMostSpecificMethod(bridgeMethod, targetClass);
		return resolveBridgeMethod(specificMethod,
				(targetClass != null ? targetClass : specificMethod.getDeclaringClass()));
	}

	private static Method resolveBridgeMethod(Method bridgeMethod, Class<?> targetClass) {
		// 是否指定类中声明的方法
		boolean localBridge = (targetClass == bridgeMethod.getDeclaringClass());
		Class<?> userClass = targetClass;
		// bridgeMethod 不是桥接方法，子类覆写的父类方法在子类中是非桥接方法，但是子类直接使用的父类方法（非覆写的方法）（该被桥接方法在父类，jdk17以前仅有这种类型的是桥接方法），
		// 以及(jdk17以前不是这样的)子类指定了明确泛型的父类泛型方法（该被桥接方法在子类），而对应形成泛型类型用Object表示的方法（该桥接方法在子类），都是桥接方法
		if (!bridgeMethod.isBridge() && localBridge) {
			userClass = ClassUtils.getUserClass(targetClass);
			if (userClass == targetClass) {
				return bridgeMethod;
			}
		}

		Object cacheKey = (localBridge ? bridgeMethod : new MethodClassKey(bridgeMethod, targetClass));
		Method bridgedMethod = cache.get(cacheKey);
		if (bridgedMethod == null) {
			// Gather all methods with matching name and parameter size.
			// 收集所有具有匹配名称和参数大小的方法。
			List<Method> candidateMethods = new ArrayList<>();
			// 过滤规则：过滤出所有类似 bridgeMethod 方法的非桥接的候选方法
			MethodFilter filter = (candidateMethod -> isBridgedCandidateFor(candidateMethod, bridgeMethod));
			// 按 filter 过滤规则，将 userClass 及其超类、超接口中满足条件的方法，添加到 candidateMethods 中
			ReflectionUtils.doWithMethods(userClass, candidateMethods::add, filter);
			if (!candidateMethods.isEmpty()) {
				bridgedMethod = (candidateMethods.size() == 1 ? candidateMethods.get(0) :
						searchCandidates(candidateMethods, bridgeMethod));
			}
			if (bridgedMethod == null) {
				// A bridge method was passed in but we couldn't find the bridged method.
				// Let's proceed with the passed-in method and hope for the best...
				// 传入了一个桥接方法，但我们找不到桥接方法。让我们继续处理传入的方法并希望获得最佳结果...
				bridgedMethod = bridgeMethod;
			}
			cache.put(cacheKey, bridgedMethod);
		}
		return bridgedMethod;
	}

	/**
	 * Returns {@code true} if the supplied '{@code candidateMethod}' can be
	 * considered a valid candidate for the {@link Method} that is {@link Method#isBridge() bridged}
	 * by the supplied {@link Method bridge Method}. This method performs inexpensive
	 * checks and can be used to quickly filter for a set of possible matches.
	 * <p>如果 candidateMethod 可视为提供的桥接方法(bridgeMethod)桥接的方法的有效候选，则返回 true。此方法执行廉价检查，可用于快速筛选一组可能的匹配项。
	 * <p>判断规则：1、candidateMethod 是非桥接方法。2、candidateMethod 和 bridgeMethod 的名称相同。3、candidateMethod 和 bridgeMethod 的参数个数相同。
	 */
	private static boolean isBridgedCandidateFor(Method candidateMethod, Method bridgeMethod) {
		return (!candidateMethod.isBridge() &&
				candidateMethod.getName().equals(bridgeMethod.getName()) &&
				candidateMethod.getParameterCount() == bridgeMethod.getParameterCount());
	}

	/**
	 * Searches for the bridged method in the given candidates.
	 * <p>从给定的候选中搜索桥接方法。
	 * @param candidateMethods the List of candidate Methods
	 * @param bridgeMethod the bridge method
	 * @return the bridged method, or {@code null} if none found
	 */
	@Nullable
	private static Method searchCandidates(List<Method> candidateMethods, Method bridgeMethod) {
		if (candidateMethods.isEmpty()) {
			return null;
		}
		Method previousMethod = null;
		boolean sameSig = true;
		for (Method candidateMethod : candidateMethods) {
			if (isBridgeMethodFor(bridgeMethod, candidateMethod, bridgeMethod.getDeclaringClass())) {
				return candidateMethod;
			}
			else if (previousMethod != null) {
				sameSig = sameSig && Arrays.equals(
						candidateMethod.getGenericParameterTypes(), previousMethod.getGenericParameterTypes());
			}
			previousMethod = candidateMethod;
		}
		return (sameSig ? candidateMethods.get(0) : null);
	}

	/**
	 * Determines whether the bridge {@link Method} is the bridge for the
	 * supplied candidate {@link Method}.
	 */
	static boolean isBridgeMethodFor(Method bridgeMethod, Method candidateMethod, Class<?> declaringClass) {
		if (isResolvedTypeMatch(candidateMethod, bridgeMethod, declaringClass)) {
			return true;
		}
		Method method = findGenericDeclaration(bridgeMethod);
		return (method != null && isResolvedTypeMatch(method, candidateMethod, declaringClass));
	}

	/**
	 * Returns {@code true} if the {@link Type} signature of both the supplied
	 * {@link Method#getGenericParameterTypes() generic Method} and concrete {@link Method}
	 * are equal after resolving all types against the declaringType, otherwise
	 * returns {@code false}.
	 */
	private static boolean isResolvedTypeMatch(Method genericMethod, Method candidateMethod, Class<?> declaringClass) {
		Type[] genericParameters = genericMethod.getGenericParameterTypes();
		if (genericParameters.length != candidateMethod.getParameterCount()) {
			return false;
		}
		Class<?>[] candidateParameters = candidateMethod.getParameterTypes();
		for (int i = 0; i < candidateParameters.length; i++) {
			ResolvableType genericParameter = ResolvableType.forMethodParameter(genericMethod, i, declaringClass);
			Class<?> candidateParameter = candidateParameters[i];
			if (candidateParameter.isArray()) {
				// An array type: compare the component type.
				if (!candidateParameter.componentType().equals(genericParameter.getComponentType().toClass())) {
					return false;
				}
			}
			// A non-array type: compare the type itself.
			if (!ClassUtils.resolvePrimitiveIfNecessary(candidateParameter).equals(
					ClassUtils.resolvePrimitiveIfNecessary(genericParameter.toClass()))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Searches for the generic {@link Method} declaration whose erased signature
	 * matches that of the supplied bridge method.
	 * @throws IllegalStateException if the generic declaration cannot be found
	 */
	@Nullable
	private static Method findGenericDeclaration(Method bridgeMethod) {
		if (!bridgeMethod.isBridge()) {
			return bridgeMethod;
		}

		// Search parent types for method that has same signature as bridge.
		Class<?> superclass = bridgeMethod.getDeclaringClass().getSuperclass();
		while (superclass != null && Object.class != superclass) {
			Method method = searchForMatch(superclass, bridgeMethod);
			if (method != null && !method.isBridge()) {
				return method;
			}
			superclass = superclass.getSuperclass();
		}

		Class<?>[] interfaces = ClassUtils.getAllInterfacesForClass(bridgeMethod.getDeclaringClass());
		return searchInterfaces(interfaces, bridgeMethod);
	}

	@Nullable
	private static Method searchInterfaces(Class<?>[] interfaces, Method bridgeMethod) {
		for (Class<?> ifc : interfaces) {
			Method method = searchForMatch(ifc, bridgeMethod);
			if (method != null && !method.isBridge()) {
				return method;
			}
			else {
				method = searchInterfaces(ifc.getInterfaces(), bridgeMethod);
				if (method != null) {
					return method;
				}
			}
		}
		return null;
	}

	/**
	 * If the supplied {@link Class} has a declared {@link Method} whose signature matches
	 * that of the supplied {@link Method}, then this matching {@link Method} is returned,
	 * otherwise {@code null} is returned.
	 */
	@Nullable
	private static Method searchForMatch(Class<?> type, Method bridgeMethod) {
		try {
			return type.getDeclaredMethod(bridgeMethod.getName(), bridgeMethod.getParameterTypes());
		}
		catch (NoSuchMethodException ex) {
			return null;
		}
	}

	/**
	 * Compare the signatures of the bridge method and the method which it bridges. If
	 * the parameter and return types are the same, it is a 'visibility' bridge method
	 * introduced in Java 6 to fix <a href="https://bugs.openjdk.org/browse/JDK-6342411">
	 * JDK-6342411</a>.
	 * @return whether signatures match as described
	 */
	public static boolean isVisibilityBridgeMethodPair(Method bridgeMethod, Method bridgedMethod) {
		if (bridgeMethod == bridgedMethod) {
			// Same method: for common purposes, return true to proceed as if it was a visibility bridge.
			return true;
		}
		if (ClassUtils.getUserClass(bridgeMethod.getDeclaringClass()) != bridgeMethod.getDeclaringClass()) {
			// Method on generated subclass: return false to consistently ignore it for visibility purposes.
			return false;
		}
		return (bridgeMethod.getReturnType().equals(bridgedMethod.getReturnType()) &&
				bridgeMethod.getParameterCount() == bridgedMethod.getParameterCount() &&
				Arrays.equals(bridgeMethod.getParameterTypes(), bridgedMethod.getParameterTypes()));
	}

}
