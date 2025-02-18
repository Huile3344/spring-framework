/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.core.type;

import org.springframework.lang.Nullable;

/**
 * Interface that defines abstract metadata of a specific class,
 * in a form that does not require that class to be loaded yet.
 * <p>以不需要加载该类的形式定义特定类的抽象元数据的接口。
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see StandardClassMetadata
 * @see org.springframework.core.type.classreading.MetadataReader#getClassMetadata()
 * @see AnnotationMetadata
 */
public interface ClassMetadata {

	/**
	 * Return the name of the underlying class.
	 * <p>返回底层类的名称。
	 */
	String getClassName();

	/**
	 * Return whether the underlying class represents an interface.
	 * <p>返回底层类是否代表接口。
	 */
	boolean isInterface();

	/**
	 * Return whether the underlying class represents an annotation.
	 * <p>返回底层类是否代表注解。
	 * @since 4.1
	 */
	boolean isAnnotation();

	/**
	 * Return whether the underlying class is marked as abstract.
	 * <p>返回底层类是否被标记为抽象。
	 */
	boolean isAbstract();

	/**
	 * Return whether the underlying class represents a concrete class,
	 * i.e. neither an interface nor an abstract class.
	 * <p>返回底层类是否代表具体类，即既不是接口也不是抽象类。
	 */
	default boolean isConcrete() {
		return !(isInterface() || isAbstract());
	}

	/**
	 * Return whether the underlying class is marked as 'final'.
	 * <p>返回底层类是否被标记为“final”。
	 */
	boolean isFinal();

	/**
	 * Determine whether the underlying class is independent, i.e. whether
	 * it is a top-level class or a nested class (static inner class) that
	 * can be constructed independently of an enclosing class.
	 * <p>确定底层类是否独立，即它是否是顶级类或可以独立于封闭类构建的嵌套类（静态内部类）。
	 */
	boolean isIndependent();

	/**
	 * Return whether the underlying class is declared within an enclosing
	 * class (i.e. the underlying class is an inner/nested class or a
	 * local class within a method).
	 * <p>If this method returns {@code false}, then the underlying
	 * class is a top-level class.
	 * <p>返回底层类是否在封闭类中声明（即底层类是内部/嵌套类还是方法内的本地类）。
	 * <p>如果此方法返回 false，则底层类是顶级类。
	 */
	default boolean hasEnclosingClass() {
		return (getEnclosingClassName() != null);
	}

	/**
	 * Return the name of the enclosing class of the underlying class,
	 * or {@code null} if the underlying class is a top-level class.
	 * <p>返回底层类的封闭类的名称，如果底层类是顶级类，则返回 null。
	 */
	@Nullable
	String getEnclosingClassName();

	/**
	 * Return whether the underlying class has a superclass.
	 * <p>返回底层类是否具有超类。
	 */
	default boolean hasSuperClass() {
		return (getSuperClassName() != null);
	}

	/**
	 * Return the name of the superclass of the underlying class,
	 * or {@code null} if there is no superclass defined.
	 * <p>返回底层类的超类的名称，如果未定义超类，则返回 null。
	 */
	@Nullable
	String getSuperClassName();

	/**
	 * Return the names of all interfaces that the underlying class
	 * implements, or an empty array if there are none.
	 * <p>返回底层类实现的所有接口的名称，如果没有，则返回一个空数组。
	 */
	String[] getInterfaceNames();

	/**
	 * Return the names of all classes declared as members of the class represented by
	 * this ClassMetadata object. This includes public, protected, default (package)
	 * access, and private classes and interfaces declared by the class, but excludes
	 * inherited classes and interfaces. An empty array is returned if no member classes
	 * or interfaces exist.
	 * <p>返回此 ClassMetadata 对象所表示的类的所有声明为成员的类的名称。这包括类声明的公共、受保护、
	 * 默认（包）访问和私有类和接口，但不包括继承的类和接口。如果不存在成员类或接口，则返回空数组。
	 * @since 3.1
	 */
	String[] getMemberClassNames();

}
