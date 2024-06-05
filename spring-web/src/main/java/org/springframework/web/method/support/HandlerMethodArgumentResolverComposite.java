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

package org.springframework.web.method.support;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Resolves method parameters by delegating to a list of registered
 * {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}.
 * Previously resolved method parameters are cached for faster lookups.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 *
 * @tips 复合的 HandlerMethodArgumentResolver 实现类。
 */
public class HandlerMethodArgumentResolverComposite implements HandlerMethodArgumentResolver {

	@Deprecated
	protected final Log logger = LogFactory.getLog(getClass());
	/**
	 * HandlerMethodArgumentResolver 数组   这就是 Composite 复合~
	 */
	private final List<HandlerMethodArgumentResolver> argumentResolvers = new LinkedList<>();
	/**
	 * MethodParameter 与 HandlerMethodArgumentResolver 的映射，作为缓存。
	 *
	 * 因为，MethodParameter 是需要从 argumentResolvers 遍历到适合其的解析器，通过缓存后，无需再次重复遍历
	 *
	 * 默认复合的所有 HandlerMethodArgumentResolver 对象。
	 */
	private final Map<MethodParameter, HandlerMethodArgumentResolver> argumentResolverCache =
			new ConcurrentHashMap<>(256);


	/**
	 * Add the given {@link HandlerMethodArgumentResolver}.
	 */
	public HandlerMethodArgumentResolverComposite addResolver(HandlerMethodArgumentResolver resolver) {
		this.argumentResolvers.add(resolver);
		return this;
	}

	/**
	 * Add the given {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}.
	 * @since 4.3
	 */
	public HandlerMethodArgumentResolverComposite addResolvers(
			@Nullable HandlerMethodArgumentResolver... resolvers) {

		if (resolvers != null) {
			Collections.addAll(this.argumentResolvers, resolvers);
		}
		return this;
	}

	/**
	 * Add the given {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}.
	 */
	public HandlerMethodArgumentResolverComposite addResolvers(
			@Nullable List<? extends HandlerMethodArgumentResolver> resolvers) {

		if (resolvers != null) {
			this.argumentResolvers.addAll(resolvers);
		}
		return this;
	}

	/**
	 * Return a read-only list with the contained resolvers, or an empty list.
	 */
	public List<HandlerMethodArgumentResolver> getResolvers() {
		return Collections.unmodifiableList(this.argumentResolvers);
	}

	/**
	 * Clear the list of configured resolvers.
	 * @since 4.3
	 */
	public void clear() {
		this.argumentResolvers.clear();
	}


	/**
	 * Whether the given {@linkplain MethodParameter method parameter} is
	 * supported by any registered {@link HandlerMethodArgumentResolver}.
	 *
	 * @tips 如果能获得到对应的 HandlerMethodArgumentResolver 处理器，则说明支持。
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return getArgumentResolver(parameter) != null;
	}

	/**
	 * Iterate over registered
	 * {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}
	 * and invoke the one that supports it.
	 * @throws IllegalArgumentException if no suitable argument resolver is found
	 *
	 * @tips ，解析指定参数的值。
	 */
	@Override
	@Nullable
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		// 获得 HandlerMethodArgumentResolver 对象
		HandlerMethodArgumentResolver resolver = getArgumentResolver(parameter);
		// 如果获得不到，抛出 IllegalArgumentException 异常
		if (resolver == null) {
			throw new IllegalArgumentException("Unsupported parameter type [" +
					parameter.getParameterType().getName() + "]. supportsParameter should be called first.");
		}
		// 执行解析
		return resolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
	}

	/**
	 * Find a registered {@link HandlerMethodArgumentResolver} that supports
	 * the given method parameter.
	 *
	 * @tips 获得方法参数对应的 HandlerMethodArgumentResolver 对象。
	 */
	@Nullable
	private HandlerMethodArgumentResolver getArgumentResolver(MethodParameter parameter) {
		// 优先从 argumentResolverCache 缓存中，获得 parameter 对应的 HandlerMethodArgumentResolver 对象
		HandlerMethodArgumentResolver result = this.argumentResolverCache.get(parameter);
		if (result == null) {
			// 获得不到，则遍历 argumentResolvers 数组，逐个判断是否支持。
			for (HandlerMethodArgumentResolver resolver : this.argumentResolvers) {
				// 如果支持，则添加到 argumentResolverCache 缓存中，并返回
				if (resolver.supportsParameter(parameter)) {
					result = resolver;
					this.argumentResolverCache.put(parameter, result);
					break;
				}
			}
		}
		return result;
	}

}
