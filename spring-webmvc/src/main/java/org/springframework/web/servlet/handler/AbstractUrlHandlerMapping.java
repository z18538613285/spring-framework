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

package org.springframework.web.servlet.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerExecutionChain;

/**
 * Abstract base class for URL-mapped {@link org.springframework.web.servlet.HandlerMapping}
 * implementations. Provides infrastructure for mapping handlers to URLs and configurable
 * URL lookup. For information on the latter, see "alwaysUseFullPath" property.
 *
 * <p>Supports direct matches, e.g. a registered "/test" matches "/test", and
 * various Ant-style pattern matches, e.g. a registered "/t*" pattern matches
 * both "/test" and "/team", "/test/*" matches all paths in the "/test" directory,
 * "/test/**" matches all paths below "/test". For details, see the
 * {@link org.springframework.util.AntPathMatcher AntPathMatcher} javadoc.
 *
 * <p>Will search all path patterns to find the most exact match for the
 * current request path. The most exact match is defined as the longest
 * path pattern that matches the current request path.
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @since 16.04.2003
 *
 * @tips 以 URL 作为 Handler 的 HandlerMapping 抽象类，提供 Handler 的获取、注册等等通用的骨架方法
 *
 */
public abstract class AbstractUrlHandlerMapping extends AbstractHandlerMapping implements MatchableHandlerMapping {
	/**
	 * 根路径的处理器
	 */
	@Nullable
	private Object rootHandler;
	/**
	 * 使用后置的 / 匹配
	 */
	private boolean useTrailingSlashMatch = false;
	/**
	 * 是否延迟加载处理器
	 *
	 * 默认，关闭。
	 */
	private boolean lazyInitHandlers = false;
	/**
	 * 路径和处理器的映射
	 *
	 * KEY：路径 {@link #lookupHandler(String, HttpServletRequest)}
	 */
	private final Map<String, Object> handlerMap = new LinkedHashMap<>();


	/**
	 * Set the root handler for this handler mapping, that is,
	 * the handler to be registered for the root path ("/").
	 * <p>Default is {@code null}, indicating no root handler.
	 */
	public void setRootHandler(@Nullable Object rootHandler) {
		this.rootHandler = rootHandler;
	}

	/**
	 * Return the root handler for this handler mapping (registered for "/"),
	 * or {@code null} if none.
	 */
	@Nullable
	public Object getRootHandler() {
		return this.rootHandler;
	}

	/**
	 * Whether to match to URLs irrespective of the presence of a trailing slash.
	 * If enabled a URL pattern such as "/users" also matches to "/users/".
	 * <p>The default value is {@code false}.
	 */
	public void setUseTrailingSlashMatch(boolean useTrailingSlashMatch) {
		this.useTrailingSlashMatch = useTrailingSlashMatch;
	}

	/**
	 * Whether to match to URLs irrespective of the presence of a trailing slash.
	 */
	public boolean useTrailingSlashMatch() {
		return this.useTrailingSlashMatch;
	}

	/**
	 * Set whether to lazily initialize handlers. Only applicable to
	 * singleton handlers, as prototypes are always lazily initialized.
	 * Default is "false", as eager initialization allows for more efficiency
	 * through referencing the controller objects directly.
	 * <p>If you want to allow your controllers to be lazily initialized,
	 * make them "lazy-init" and set this flag to true. Just making them
	 * "lazy-init" will not work, as they are initialized through the
	 * references from the handler mapping in this case.
	 */
	public void setLazyInitHandlers(boolean lazyInitHandlers) {
		this.lazyInitHandlers = lazyInitHandlers;
	}

	/**
	 * Look up a handler for the URL path of the given request.
	 * @param request current HTTP request
	 * @return the handler instance, or {@code null} if none found
	 *
	 * @tips 根据 URL 找到匹配的Controller并返回，当然如果没有找到对应的 Controller 处理器，那么程序会尝试去查找配置中
	 * 的默认处理器，当然，当查找的 controller 为 String 类型时，那就意味着返回的是配置的 bean名称，
	 * 需要根据 bean名称查找对应的 bean，最后还要通过 getHandlerExecutionChain 方法对返回的 Handler 进行封装，
	 * 以保证满足返回类型的匹配。
	 */
	@Override
	@Nullable
	protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
		// 截取用于匹配的 url 有效路径
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		// 根据路径寻找 Handler
		Object handler = lookupHandler(lookupPath, request);
		// <3> 如果找不到处理器，则使用 rootHandler 或 defaultHandler 处理器
		if (handler == null) {
			// We need to care for the default handler directly, since we need to
			// expose the PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE for it as well.
			Object rawHandler = null;
			if ("/".equals(lookupPath)) {
				// 如果请求的路径仅仅是 “/” 那么使用 RootHandler 进行处理
				rawHandler = getRootHandler();
			}
			if (rawHandler == null) {
				// 无法找到 handler 则使用默认 handler
				rawHandler = getDefaultHandler();
			}
			if (rawHandler != null) {
				// Bean name or resolved handler?
				// 根据 beanName 获取对应的 bean
				if (rawHandler instanceof String) {
					String handlerName = (String) rawHandler;
					rawHandler = obtainApplicationContext().getBean(handlerName);
				}
				// 模板方法
				// <3.4> 空方法，校验处理器。目前暂无子类实现该方
				validateHandler(rawHandler, request);
				// <3.5> 创建处理器
				handler = buildPathExposingHandler(rawHandler, lookupPath, lookupPath, null);
			}
		}
		return handler;
	}

	/**
	 * Look up a handler instance for the given URL path.
	 * <p>Supports direct matches, e.g. a registered "/test" matches "/test",
	 * and various Ant-style pattern matches, e.g. a registered "/t*" matches
	 * both "/test" and "/team". For details, see the AntPathMatcher class.
	 * <p>Looks for the most exact pattern, where most exact is defined as
	 * the longest path pattern.
	 * @param urlPath the URL the bean is mapped to
	 * @param request current HTTP request (to expose the path within the mapping to)
	 * @return the associated handler instance, or {@code null} if not found
	 * @see #exposePathWithinMapping
	 * @see org.springframework.util.AntPathMatcher
	 *
	 * @tips 获得处理器
	 *
	 */
	@Nullable
	protected Object lookupHandler(String urlPath, HttpServletRequest request) throws Exception {
		// Direct match?
		// 直接匹配情况的处理
		// <1.1> 情况一，从 handlerMap 中，直接匹配处理器
		Object handler = this.handlerMap.get(urlPath);
		if (handler != null) {
			// Bean name or resolved handler?
			// <1.2> 如果找到的处理器是 String 类型，则从容器中找到 String 对应的 Bean 类型作为处理器。
			if (handler instanceof String) {
				String handlerName = (String) handler;
				handler = obtainApplicationContext().getBean(handlerName);
			}
			// <1.3> 空方法，校验处理器。目前暂无子类实现该方法
			validateHandler(handler, request);
			// 将 Handler 封装为 HandlerExecutionChain 类型
			return buildPathExposingHandler(handler, urlPath, urlPath, null);
		}

		// Pattern match?
		// 通配符匹配的处理
		// <2.1> 情况二，Pattern 匹配合适的，并添加到 matchingPatterns 中
		List<String> matchingPatterns = new ArrayList<>();
		for (String registeredPattern : this.handlerMap.keySet()) {
			if (getPathMatcher().match(registeredPattern, urlPath)) {
				matchingPatterns.add(registeredPattern);
			}
			else if (useTrailingSlashMatch()) {
				if (!registeredPattern.endsWith("/") && getPathMatcher().match(registeredPattern + "/", urlPath)) {
					matchingPatterns.add(registeredPattern + "/");
				}
			}
		}

		// <2.2> 获得首个匹配的结果
		String bestMatch = null;
		Comparator<String> patternComparator = getPathMatcher().getPatternComparator(urlPath);
		if (!matchingPatterns.isEmpty()) {
			matchingPatterns.sort(patternComparator); // 排序
			if (logger.isTraceEnabled() && matchingPatterns.size() > 1) {
				logger.trace("Matching patterns " + matchingPatterns);
			}
			bestMatch = matchingPatterns.get(0);
		}
		if (bestMatch != null) {
			// <2.3> 获得 bestMatch 对应的处理器
			handler = this.handlerMap.get(bestMatch);
			if (handler == null) {
				if (bestMatch.endsWith("/")) {
					handler = this.handlerMap.get(bestMatch.substring(0, bestMatch.length() - 1));
				}
				// 如果获得不到，抛出 IllegalStateException 异常
				if (handler == null) {
					throw new IllegalStateException(
							"Could not find handler for best pattern match [" + bestMatch + "]");
				}
			}
			// Bean name or resolved handler?
			// <2.4> 如果找到的处理器是 String 类型，则从容器中找到 String 对应的 Bean 类型作为处理器。
			if (handler instanceof String) {
				String handlerName = (String) handler;
				handler = obtainApplicationContext().getBean(handlerName);
			}
			// <2.5> 空方法，校验处理器。目前暂无子类实现该方法
			validateHandler(handler, request);
			// <2.6> 获得匹配的路径
			String pathWithinMapping = getPathMatcher().extractPathWithinPattern(bestMatch, urlPath);

			// There might be multiple 'best patterns', let's make sure we have the correct URI template variables
			// for all of them
			// <2.7> 获得路径参数集合
			Map<String, String> uriTemplateVariables = new LinkedHashMap<>();
			for (String matchingPattern : matchingPatterns) {
				if (patternComparator.compare(bestMatch, matchingPattern) == 0) {
					Map<String, String> vars = getPathMatcher().extractUriTemplateVariables(matchingPattern, urlPath);
					Map<String, String> decodedVars = getUrlPathHelper().decodePathVariables(request, vars);
					uriTemplateVariables.putAll(decodedVars);
				}
			}
			if (logger.isTraceEnabled() && uriTemplateVariables.size() > 0) {
				logger.trace("URI variables " + uriTemplateVariables);
			}
			// <2.8> 创建处理器
			return buildPathExposingHandler(handler, bestMatch, pathWithinMapping, uriTemplateVariables);
		}

		// No handler found...
		return null;
	}

	/**
	 * Validate the given handler against the current request.
	 * <p>The default implementation is empty. Can be overridden in subclasses,
	 * for example to enforce specific preconditions expressed in URL mappings.
	 * @param handler the handler object to validate
	 * @param request current HTTP request
	 * @throws Exception if validation failed
	 */
	protected void validateHandler(Object handler, HttpServletRequest request) throws Exception {
	}

	/**
	 * Build a handler object for the given raw handler, exposing the actual
	 * handler, the {@link #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}, as well as
	 * the {@link #URI_TEMPLATE_VARIABLES_ATTRIBUTE} before executing the handler.
	 * <p>The default implementation builds a {@link HandlerExecutionChain}
	 * with a special interceptor that exposes the path attribute and uri template variables
	 * @param rawHandler the raw handler to expose
	 * @param pathWithinMapping the path to expose before executing the handler
	 * @param uriTemplateVariables the URI template variables, can be {@code null} if no variables found
	 * @return the final handler object
	 *
	 * @tips 通过将 Handler 以参数传入，并构建 HandlerExecutionChain 类型实例，加入了两个拦截器。
	 * 此时我们似乎已经了解了Spring 这样大费周折的目的。链处理机制，是Spring中非常常见的处理方式，是 AOP中的重要组成部分，
	 * 可以方便地对目标对象进行扩展及拦截，这是非常优秀的设计。
	 */
	protected Object buildPathExposingHandler(Object rawHandler, String bestMatchingPattern,
			String pathWithinMapping, @Nullable Map<String, String> uriTemplateVariables) {

		// <1> 创建 HandlerExecutionChain 对象
		HandlerExecutionChain chain = new HandlerExecutionChain(rawHandler);
		// <2.1> 添加 PathExposingHandlerInterceptor 拦截器，到 chain 中
		chain.addInterceptor(new PathExposingHandlerInterceptor(bestMatchingPattern, pathWithinMapping));
		// <2.2> 添加 UriTemplateVariablesHandlerInterceptor 拦截器，到 chain 中
		if (!CollectionUtils.isEmpty(uriTemplateVariables)) {
			chain.addInterceptor(new UriTemplateVariablesHandlerInterceptor(uriTemplateVariables));
		}
		return chain;
	}

	/**
	 * Expose the path within the current mapping as request attribute.
	 * @param pathWithinMapping the path within the current mapping
	 * @param request the request to expose the path to
	 * @see #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
	 */
	protected void exposePathWithinMapping(String bestMatchingPattern, String pathWithinMapping,
			HttpServletRequest request) {

		request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, bestMatchingPattern);
		request.setAttribute(PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, pathWithinMapping);
	}

	/**
	 * Expose the URI templates variables as request attribute.
	 * @param uriTemplateVariables the URI template variables
	 * @param request the request to expose the path to
	 * @see #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
	 */
	protected void exposeUriTemplateVariables(Map<String, String> uriTemplateVariables, HttpServletRequest request) {
		request.setAttribute(URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriTemplateVariables);
	}

	/**
	 * 执行匹配
	 * @param request the current request
	 * @param pattern the pattern to match
	 * @return
	 */
	@Override
	@Nullable
	public RequestMatchResult match(HttpServletRequest request, String pattern) {
		// 获得请求路径
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		// 模式匹配，若匹配，则返回 RequestMatchResult 对象
		if (getPathMatcher().match(pattern, lookupPath)) {
			return new RequestMatchResult(pattern, lookupPath, getPathMatcher());
		}
		else if (useTrailingSlashMatch()) {
			if (!pattern.endsWith("/") && getPathMatcher().match(pattern + "/", lookupPath)) {
				return new RequestMatchResult(pattern + "/", lookupPath, getPathMatcher());
			}
		}
		// 不匹配，则返回 null
		return null;
	}

	/**
	 * Register the specified handler for the given URL paths.
	 * @param urlPaths the URLs that the bean should be mapped to
	 * @param beanName the name of the handler bean
	 * @throws BeansException if the handler couldn't be registered
	 * @throws IllegalStateException if there is a conflicting handler registered
	 *
	 * @tips 注册多个 URL 的处理器
	 */
	protected void registerHandler(String[] urlPaths, String beanName) throws BeansException, IllegalStateException {
		Assert.notNull(urlPaths, "URL path array must not be null");
		for (String urlPath : urlPaths) {
			registerHandler(urlPath, beanName);
		}
	}

	/**
	 * Register the specified handler for the given URL path.
	 * @param urlPath the URL the bean should be mapped to
	 * @param handler the handler instance or handler bean name String
	 * (a bean name will automatically be resolved into the corresponding handler bean)
	 * @throws BeansException if the handler couldn't be registered
	 * @throws IllegalStateException if there is a conflicting handler registered
	 *
	 * @tips 注册单个 URL 的处理器
	 */
	protected void registerHandler(String urlPath, Object handler) throws BeansException, IllegalStateException {
		Assert.notNull(urlPath, "URL path must not be null");
		Assert.notNull(handler, "Handler object must not be null");
		Object resolvedHandler = handler;

		// Eagerly resolve handler if referencing singleton via name.
		// <1> 如果非延迟加载，并且 handler 为 String 类型，并且还是单例，则去获取 String 对应的 Bean 对象
		if (!this.lazyInitHandlers && handler instanceof String) {
			String handlerName = (String) handler;
			ApplicationContext applicationContext = obtainApplicationContext();
			if (applicationContext.isSingleton(handlerName)) {
				resolvedHandler = applicationContext.getBean(handlerName);
			}
		}

		// <2> 获得 urlPath 对应的处理器
		Object mappedHandler = this.handlerMap.get(urlPath);
		// <3> 如果已经存在，并且和 resolvedHandler 不同，则抛出 IllegalStateException 异常
		if (mappedHandler != null) {
			if (mappedHandler != resolvedHandler) {
				throw new IllegalStateException(
						"Cannot map " + getHandlerDescription(handler) + " to URL path [" + urlPath +
						"]: There is already " + getHandlerDescription(mappedHandler) + " mapped.");
			}
		}
		else {
			// <4.1> 如果是 / 根路径，则设置为 rootHandler
			if (urlPath.equals("/")) {
				if (logger.isTraceEnabled()) {
					logger.trace("Root mapping to " + getHandlerDescription(handler));
				}
				setRootHandler(resolvedHandler);
			}
			// <4.2> 如果是 /* 路径，则设置为默认处理器
			else if (urlPath.equals("/*")) {
				if (logger.isTraceEnabled()) {
					logger.trace("Default mapping to " + getHandlerDescription(handler));
				}
				setDefaultHandler(resolvedHandler);
			}
			// <4.3> 添加到 handlerMap 中
			else {
				this.handlerMap.put(urlPath, resolvedHandler);
				if (logger.isTraceEnabled()) {
					logger.trace("Mapped [" + urlPath + "] onto " + getHandlerDescription(handler));
				}
			}
		}
	}

	private String getHandlerDescription(Object handler) {
		return (handler instanceof String ? "'" + handler + "'" : handler.toString());
	}


	/**
	 * Return the registered handlers as an unmodifiable Map, with the registered path
	 * as key and the handler object (or handler bean name in case of a lazy-init handler)
	 * as value.
	 * @see #getDefaultHandler()
	 */
	public final Map<String, Object> getHandlerMap() {
		return Collections.unmodifiableMap(this.handlerMap);
	}

	/**
	 * Indicates whether this handler mapping support type-level mappings. Default to {@code false}.
	 */
	protected boolean supportsTypeLevelMappings() {
		return false;
	}


	/**
	 * Special interceptor for exposing the
	 * {@link AbstractUrlHandlerMapping#PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE} attribute.
	 * @see AbstractUrlHandlerMapping#exposePathWithinMapping
	 */
	private class PathExposingHandlerInterceptor extends HandlerInterceptorAdapter {
		/**
		 * 最佳匹配的路径
		 */
		private final String bestMatchingPattern;
		/**
		 * 被匹配的路径
		 */
		private final String pathWithinMapping;

		public PathExposingHandlerInterceptor(String bestMatchingPattern, String pathWithinMapping) {
			this.bestMatchingPattern = bestMatchingPattern;
			this.pathWithinMapping = pathWithinMapping;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
			// 暴露 BEST_MATCHING_PATTERN_ATTRIBUTE、PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE 属性
			exposePathWithinMapping(this.bestMatchingPattern, this.pathWithinMapping, request);
			request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, handler);
			// 暴露 INTROSPECT_TYPE_LEVEL_MAPPING 属性
			request.setAttribute(INTROSPECT_TYPE_LEVEL_MAPPING, supportsTypeLevelMappings());
			return true;
		}

	}

	/**
	 * Special interceptor for exposing the
	 * {@link AbstractUrlHandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE} attribute.
	 * @see AbstractUrlHandlerMapping#exposePathWithinMapping
	 */
	private class UriTemplateVariablesHandlerInterceptor extends HandlerInterceptorAdapter {

		private final Map<String, String> uriTemplateVariables;

		public UriTemplateVariablesHandlerInterceptor(Map<String, String> uriTemplateVariables) {
			this.uriTemplateVariables = uriTemplateVariables;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
			exposeUriTemplateVariables(this.uriTemplateVariables, request);
			return true;
		}
	}

}
