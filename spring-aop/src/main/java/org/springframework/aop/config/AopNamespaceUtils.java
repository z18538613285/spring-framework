/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.aop.config;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.lang.Nullable;

/**
 * Utility class for handling registration of auto-proxy creators used internally
 * by the '{@code aop}' namespace tags.
 *
 * <p>Only a single auto-proxy creator should be registered and multiple configuration
 * elements may wish to register different concrete implementations. As such this class
 * delegates to {@link AopConfigUtils} which provides a simple escalation protocol.
 * Callers may request a particular auto-proxy creator and know that creator,
 * <i>or a more capable variant thereof</i>, will be registered as a post-processor.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.0
 * @see AopConfigUtils
 */
public abstract class AopNamespaceUtils {

	/**
	 * The {@code proxy-target-class} attribute as found on AOP-related XML tags.
	 */
	public static final String PROXY_TARGET_CLASS_ATTRIBUTE = "proxy-target-class";

	/**
	 * The {@code expose-proxy} attribute as found on AOP-related XML tags.
	 */
	private static final String EXPOSE_PROXY_ATTRIBUTE = "expose-proxy";


	public static void registerAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		BeanDefinition beanDefinition = AopConfigUtils.registerAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	public static void registerAspectJAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	public static void registerAspectJAnnotationAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		/**
		 * 注册或者升级 AutoProxyCreator 定义 beanName为 internalAutoProxyCreator 的 BeanDefinition
		 */
		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		// 对于 proxy-target-class 以及 exposs-proxy 属性的处理
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		/**
		 * 注册组件并通知，便于监听器做进一步处理
		 * 其中 beanDefinition的 className 为 AnnotationAwareAspectJAutoProxyCreator
		 */
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	/**
	 * proxy-target-class ： Spring AOP 部分使用 JDK 动态代理或者 CGLIB 来为目标对象创建代理（建议尽量使用JDK的动态代理）。
	 * 如果被代理的目标对象实现了至少一个接口，则会使用 JDK 动态代理。
	 * 所有该目标类型试下的接口都将没代理，若该目标对象没有实现任何接口，则创建一个 CGLIB 代理。如果你希望强制使用 CGLIB 代理（例如
	 * 希望代理目标对象的所有方法，而不只是实现自接口的方法），那也可以。但是需要考虑以下两个问题：
	 * 		无法通知（advise）Final 方法，因为它们不能被覆写。
	 * 		你需要将 CGLIB 二进制发行包放在 classpath 下面
	 *
	 * 与之相比，JDK 本身就提供了动态代理，强制使用 CGLIB 代理需要将 <aop:config> 的 proxy-target-class 属性设为true
	 * <aop:config proxy-target-class="true"> ... </aop:config>
	 * 当需要使用 CGLIB 代理和 @AspectJ 自动代理支持，可以按照以下方式设置 <aop:aspectj-autoproxy> 的 proxy-target-class 属性
	 * <aop:aspectj-autoproxy proxy-target-class="true"/>
	 *
	 * 而实际使用的过程中有些细节问题的差别
	 * 1、JDK 动态代理：其代理对象必须是某个接口的实现，它是通过在运行期间创建一个接口的实现类来完成对目标对象的代理
	 * 2、CGLIB 代理：实现原理类似于 JDK 动态代理，只是它在运行期间生成的代理对象是针对目标类扩展的子类。
	 * 		CGLIB 是高效的代理生成包，底层是依靠 ASM （开源的 Java字节码编辑类库）操作字节码实现的，性能比JDK强。
	 * 3、expose-proxy：有时候目标对象内部的自我调用将无法实施切面中的增强。
	 *
	 * @param registry
	 * @param sourceElement
	 */
	private static void useClassProxyingIfNecessary(BeanDefinitionRegistry registry, @Nullable Element sourceElement) {
		if (sourceElement != null) {
			boolean proxyTargetClass = Boolean.parseBoolean(sourceElement.getAttribute(PROXY_TARGET_CLASS_ATTRIBUTE));
			if (proxyTargetClass) {
				// 对于 proxy-target-class 属性的处理
				AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
			}
			boolean exposeProxy = Boolean.parseBoolean(sourceElement.getAttribute(EXPOSE_PROXY_ATTRIBUTE));
			if (exposeProxy) {
				// 对于 expose-proxy 属性的处理
				AopConfigUtils.forceAutoProxyCreatorToExposeProxy(registry);
			}
		}
	}

	private static void registerComponentIfNecessary(@Nullable BeanDefinition beanDefinition, ParserContext parserContext) {
		if (beanDefinition != null) {
			parserContext.registerComponent(
					new BeanComponentDefinition(beanDefinition, AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME));
		}
	}

}
