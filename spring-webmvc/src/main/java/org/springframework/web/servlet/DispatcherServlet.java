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

package org.springframework.web.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.ui.context.ThemeSource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.WebUtils;

/**
 * Central dispatcher for HTTP request handlers/controllers, e.g. for web UI controllers
 * or HTTP-based remote service exporters. Dispatches to registered handlers for processing
 * a web request, providing convenient mapping and exception handling facilities.
 *
 * <p>This servlet is very flexible: It can be used with just about any workflow, with the
 * installation of the appropriate adapter classes. It offers the following functionality
 * that distinguishes it from other request-driven web MVC frameworks:
 *
 * <ul>
 * <li>It is based around a JavaBeans configuration mechanism.
 *
 * <li>It can use any {@link HandlerMapping} implementation - pre-built or provided as part
 * of an application - to control the routing of requests to handler objects. Default is
 * {@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping} and
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}.
 * HandlerMapping objects can be defined as beans in the servlet's application context,
 * implementing the HandlerMapping interface, overriding the default HandlerMapping if
 * present. HandlerMappings can be given any bean name (they are tested by type).
 *
 * <li>It can use any {@link HandlerAdapter}; this allows for using any handler interface.
 * Default adapters are {@link org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter},
 * {@link org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter}, for Spring's
 * {@link org.springframework.web.HttpRequestHandler} and
 * {@link org.springframework.web.servlet.mvc.Controller} interfaces, respectively. A default
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter}
 * will be registered as well. HandlerAdapter objects can be added as beans in the
 * application context, overriding the default HandlerAdapters. Like HandlerMappings,
 * HandlerAdapters can be given any bean name (they are tested by type).
 *
 * <li>The dispatcher's exception resolution strategy can be specified via a
 * {@link HandlerExceptionResolver}, for example mapping certain exceptions to error pages.
 * Default are
 * {@link org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver},
 * {@link org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver}, and
 * {@link org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver}.
 * These HandlerExceptionResolvers can be overridden through the application context.
 * HandlerExceptionResolver can be given any bean name (they are tested by type).
 *
 * <li>Its view resolution strategy can be specified via a {@link ViewResolver}
 * implementation, resolving symbolic view names into View objects. Default is
 * {@link org.springframework.web.servlet.view.InternalResourceViewResolver}.
 * ViewResolver objects can be added as beans in the application context, overriding the
 * default ViewResolver. ViewResolvers can be given any bean name (they are tested by type).
 *
 * <li>If a {@link View} or view name is not supplied by the user, then the configured
 * {@link RequestToViewNameTranslator} will translate the current request into a view name.
 * The corresponding bean name is "viewNameTranslator"; the default is
 * {@link org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator}.
 *
 * <li>The dispatcher's strategy for resolving multipart requests is determined by a
 * {@link org.springframework.web.multipart.MultipartResolver} implementation.
 * Implementations for Apache Commons FileUpload and Servlet 3 are included; the typical
 * choice is {@link org.springframework.web.multipart.commons.CommonsMultipartResolver}.
 * The MultipartResolver bean name is "multipartResolver"; default is none.
 *
 * <li>Its locale resolution strategy is determined by a {@link LocaleResolver}.
 * Out-of-the-box implementations work via HTTP accept header, cookie, or session.
 * The LocaleResolver bean name is "localeResolver"; default is
 * {@link org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver}.
 *
 * <li>Its theme resolution strategy is determined by a {@link ThemeResolver}.
 * Implementations for a fixed theme and for cookie and session storage are included.
 * The ThemeResolver bean name is "themeResolver"; default is
 * {@link org.springframework.web.servlet.theme.FixedThemeResolver}.
 * </ul>
 *
 * <p><b>NOTE: The {@code @RequestMapping} annotation will only be processed if a
 * corresponding {@code HandlerMapping} (for type-level annotations) and/or
 * {@code HandlerAdapter} (for method-level annotations) is present in the dispatcher.</b>
 * This is the case by default. However, if you are defining custom {@code HandlerMappings}
 * or {@code HandlerAdapters}, then you need to make sure that a corresponding custom
 * {@code RequestMappingHandlerMapping} and/or {@code RequestMappingHandlerAdapter}
 * is defined as well - provided that you intend to use {@code @RequestMapping}.
 *
 * <p><b>A web application can define any number of DispatcherServlets.</b>
 * Each servlet will operate in its own namespace, loading its own application context
 * with mappings, handlers, etc. Only the root application context as loaded by
 * {@link org.springframework.web.context.ContextLoaderListener}, if any, will be shared.
 *
 * <p>As of Spring 3.1, {@code DispatcherServlet} may now be injected with a web
 * application context, rather than creating its own internally. This is useful in Servlet
 * 3.0+ environments, which support programmatic registration of servlet instances.
 * See the {@link #DispatcherServlet(WebApplicationContext)} javadoc for details.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Chris Beams
 * @author Rossen Stoyanchev
 * @see org.springframework.web.HttpRequestHandler
 * @see org.springframework.web.servlet.mvc.Controller
 * @see org.springframework.web.context.ContextLoaderListener
 *
 * @tips 在 Spring中，ContextLoaderListener只是辅助功能，用于创建 WebApplicationContext 类型实例，
 * 而真正的逻辑实现其实是在 DispatcherServlet 中进行的，DispatcherServlet 是实现servlet接口的实现类
 * servlet 是一个 Java 编写的程序，此程序时基于 HTTP 协议的，在服务器端运行的（如 Tomcat），是按照 servlet规范编写的一个 Java类。
 * 主要是处理客户端的请求并将其发送到客户端。servlet的生命周期是由 servlet 的容器拉控制的，它可以分为 3 个阶段：初始化、运行和销毁
 * 1、初始化阶段
 * 	（1）servlet容器加载 servlet类，把servlet类的 .class 文件中的数据读取到内存中。
 * 	（2）servlet容器创建一个 ServletConfig 对象。ServletConfig 对象包含了 servlet 的初始化配置信息。
 * 	（3）servlet容器创建一个 servlet对象
 * 	（4）servlet容器调用 servlet 对象的init方法进行初始化
 * 2、运行阶段
 *   当 servlet 容器接收一个请求时，servlet 容器会针对这个请求创建 servletRequest 和 servletResponse 对象，然后调用 service 方法。
 *   并把这两个参数传递给 service 方法。service 方法通过 servletRequest 对象获取请求的信息，并处理请求。再通过servletResponse
 *   对象生成这个请求的响应结果。然后销毁 servletRequest 和 servletResponse 对象，不管这个请求时 post 还是 get 提交的，最终这个请求
 *   都会由service 方法处理
 * 3、销毁阶段
 *   当 Web 应用被销毁时，servlet 容器会先调用 servlet 对象的 destroy 方法，然后再销毁 servlet对象，同时也会销毁与 servlet 对象相关联的
 *   servletConfig 对象。我们可以在 destroy 方法的实现中，释放 servlet 所占用的资源，如关闭数据库连接，关闭文件输入输出流等。
 *
 * servlet的框架是由两个 Java 包组成：javax.servlet 和 javax.servlet.http。在javax.servlet 包中定义了所有的 servlet 类都必须要实现
 * 或扩展的通用接口和类，在 javax.servlet.http 包中定义了采用 HTTP 通信协议的 HttpServlet 类
 * servlet 被设计成请求驱动，servlet 的请求可能包含多个数据项，当 Web容器接收到某个servlet 请求时，servlet 会把请求封装成一个 HttpServletRequest
 * 对象，然后把对象传给 sevlet的对应的服务方法。
 * HTTP 的请求方式包括 delete、get、options、post、put 和 trace，在HttpServlet 类中分别提供了相应的服务方法，
 * 它们是 doDelete、doGet、doOptions、doPost、doPut 和 doTrace 方法
 *
 *
 * 负责初始化 Spring MVC 的各个组件，以及处理客户端的请求
 */
@SuppressWarnings("serial")
public class DispatcherServlet extends FrameworkServlet {

	/** Well-known name for the MultipartResolver object in the bean factory for this namespace. */
	public static final String MULTIPART_RESOLVER_BEAN_NAME = "multipartResolver";

	/** Well-known name for the LocaleResolver object in the bean factory for this namespace. */
	public static final String LOCALE_RESOLVER_BEAN_NAME = "localeResolver";

	/** Well-known name for the ThemeResolver object in the bean factory for this namespace. */
	public static final String THEME_RESOLVER_BEAN_NAME = "themeResolver";

	/**
	 * Well-known name for the HandlerMapping object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerMappings" is turned off.
	 * @see #setDetectAllHandlerMappings
	 */
	public static final String HANDLER_MAPPING_BEAN_NAME = "handlerMapping";

	/**
	 * Well-known name for the HandlerAdapter object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerAdapters" is turned off.
	 * @see #setDetectAllHandlerAdapters
	 */
	public static final String HANDLER_ADAPTER_BEAN_NAME = "handlerAdapter";

	/**
	 * Well-known name for the HandlerExceptionResolver object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerExceptionResolvers" is turned off.
	 * @see #setDetectAllHandlerExceptionResolvers
	 */
	public static final String HANDLER_EXCEPTION_RESOLVER_BEAN_NAME = "handlerExceptionResolver";

	/**
	 * Well-known name for the RequestToViewNameTranslator object in the bean factory for this namespace.
	 */
	public static final String REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME = "viewNameTranslator";

	/**
	 * Well-known name for the ViewResolver object in the bean factory for this namespace.
	 * Only used when "detectAllViewResolvers" is turned off.
	 * @see #setDetectAllViewResolvers
	 */
	public static final String VIEW_RESOLVER_BEAN_NAME = "viewResolver";

	/**
	 * Well-known name for the FlashMapManager object in the bean factory for this namespace.
	 */
	public static final String FLASH_MAP_MANAGER_BEAN_NAME = "flashMapManager";

	/**
	 * Request attribute to hold the current web application context.
	 * Otherwise only the global web app context is obtainable by tags etc.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#findWebApplicationContext
	 */
	public static final String WEB_APPLICATION_CONTEXT_ATTRIBUTE = DispatcherServlet.class.getName() + ".CONTEXT";

	/**
	 * Request attribute to hold the current LocaleResolver, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getLocaleResolver
	 */
	public static final String LOCALE_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".LOCALE_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeResolver, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeResolver
	 */
	public static final String THEME_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeSource, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeSource
	 */
	public static final String THEME_SOURCE_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_SOURCE";

	/**
	 * Name of request attribute that holds a read-only {@code Map<String,?>}
	 * with "input" flash attributes saved by a previous request, if any.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getInputFlashMap(HttpServletRequest)
	 */
	public static final String INPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".INPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the "output" {@link FlashMap} with
	 * attributes to save for a subsequent request.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getOutputFlashMap(HttpServletRequest)
	 */
	public static final String OUTPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".OUTPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the {@link FlashMapManager}.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getFlashMapManager(HttpServletRequest)
	 */
	public static final String FLASH_MAP_MANAGER_ATTRIBUTE = DispatcherServlet.class.getName() + ".FLASH_MAP_MANAGER";

	/**
	 * Name of request attribute that exposes an Exception resolved with an
	 * {@link HandlerExceptionResolver} but where no view was rendered
	 * (e.g. setting the status code).
	 */
	public static final String EXCEPTION_ATTRIBUTE = DispatcherServlet.class.getName() + ".EXCEPTION";

	/** Log category to use when no mapped handler is found for a request. */
	public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

	/**
	 * Name of the class path resource (relative to the DispatcherServlet class)
	 * that defines DispatcherServlet's default strategy names.
	 */
	private static final String DEFAULT_STRATEGIES_PATH = "DispatcherServlet.properties";

	/**
	 * Common prefix that DispatcherServlet's default strategy attributes start with.
	 */
	private static final String DEFAULT_STRATEGIES_PREFIX = "org.springframework.web.servlet";

	/** Additional logger to use when no mapped handler is found for a request. */
	protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);

	private static final Properties defaultStrategies;

	static {
		// Load default strategy implementations from properties file.
		// This is currently strictly internal and not meant to be customized
		// by application developers.
		try {
			// DEFAULT_STRATEGIES_PATH = "DispatcherServlet.properties";
			/**
			 * 系统加载的时候，defaultStrategies 根据当前路径 DispatcherServlet.properties 来初始化本身，
			 * 查看 DispatcherServlet.properties 中对应于 HandlerAdapter 的属性
			 *
			 * 		org.springframework.web.servlet.HandlerAdapter=org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter,\
			 * 			org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter,\
			 * 			org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
			 * 	由此得知，如果程序开发人员没有在配置文件中定义自己的适配器，那么 Spring 会默认加载配置文件中的 3 个适配器
			 *
			 * 作为总控制器的派遣器 servlet 通过处理器映射得到处理器后，会轮训处理器适配器模块，查找能够处理当前 HTTP 请求的处理器适配器的实现，
			 * 处理器适配器模块根据处理器映射返回的处理器类型，例如简单的控制器类型，注解控制器类型或者远程调用处理器类型，来选择某一个适当的处理器
			 * 适配器的实现，从而适配当前的 HTTP 请求。
			 *
			 * HTTP 请求处理器适配器（HttpRequestHandlerAdapter）
			 * 		HTTP 请求处理器适配器仅仅支持对 HTTP 请求处理器的适配，他简单地将HTTP 请求对象和响应对象传递给 HTTP 请求处理器的实现，他不需要返回值。
			 * 		它主要应用在基于 HTTP 的远程调用的实现上
			 * 简单控制器处理器适配器（SimpleControllerHandlerAdapter）
			 * 		这个实现将 HTTP 请求适配到一个控制器的实现进行处理。这里控制器的实现是一个简单的控制器接口的实现。简单控制器处理器适配器被设计成
			 * 	    一个框架类的实现，不需要被改写，客户化的业务逻辑通常是在控制器接口的实现类中实现的。
			 * 注解方法处理器适配器（AnnotationMethodHandlerAdapter）
			 * 		这个类的实现是基于注解的实现，他需要结合注解方法映射和注解方法处理器协同工作，它通过解析声明在注解控制器的请求映射信息来解析相应的处理器方法
			 * 		来处理当前的 HTTP 请求。在处理的过程中，它通过反射来发现探测处理器方法的参数，调用处理器方法，并且映射返回值到模型和控制器对象，最后返回模型和
			 * 		控制器对象给作为主控制器的派遣器 servlet
			 *
			 *
			 */
			ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH, DispatcherServlet.class);
			defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not load '" + DEFAULT_STRATEGIES_PATH + "': " + ex.getMessage());
		}
	}

	/** Detect all HandlerMappings or just expect "handlerMapping" bean?. */
	private boolean detectAllHandlerMappings = true;

	/** Detect all HandlerAdapters or just expect "handlerAdapter" bean?. */
	private boolean detectAllHandlerAdapters = true;

	/** Detect all HandlerExceptionResolvers or just expect "handlerExceptionResolver" bean?. */
	private boolean detectAllHandlerExceptionResolvers = true;

	/** Detect all ViewResolvers or just expect "viewResolver" bean?. */
	private boolean detectAllViewResolvers = true;

	/** Throw a NoHandlerFoundException if no Handler was found to process this request? *.*/
	private boolean throwExceptionIfNoHandlerFound = false;

	/** Perform cleanup of request attributes after include request?. */
	private boolean cleanupAfterInclude = true;

	/** MultipartResolver used by this servlet. */
	@Nullable
	private MultipartResolver multipartResolver;

	/** LocaleResolver used by this servlet. */
	@Nullable
	private LocaleResolver localeResolver;

	/** ThemeResolver used by this servlet. */
	@Nullable
	private ThemeResolver themeResolver;

	/** List of HandlerMappings used by this servlet. */
	@Nullable
	private List<HandlerMapping> handlerMappings;

	/** List of HandlerAdapters used by this servlet. */
	@Nullable
	private List<HandlerAdapter> handlerAdapters;

	/** List of HandlerExceptionResolvers used by this servlet. */
	@Nullable
	private List<HandlerExceptionResolver> handlerExceptionResolvers;

	/** RequestToViewNameTranslator used by this servlet. */
	@Nullable
	private RequestToViewNameTranslator viewNameTranslator;

	/** FlashMapManager used by this servlet. */
	@Nullable
	private FlashMapManager flashMapManager;

	/** List of ViewResolvers used by this servlet. */
	@Nullable
	private List<ViewResolver> viewResolvers;


	/**
	 * Create a new {@code DispatcherServlet} that will create its own internal web
	 * application context based on defaults and values provided through servlet
	 * init-params. Typically used in Servlet 2.5 or earlier environments, where the only
	 * option for servlet registration is through {@code web.xml} which requires the use
	 * of a no-arg constructor.
	 * <p>Calling {@link #setContextConfigLocation} (init-param 'contextConfigLocation')
	 * will dictate which XML files will be loaded by the
	 * {@linkplain #DEFAULT_CONTEXT_CLASS default XmlWebApplicationContext}
	 * <p>Calling {@link #setContextClass} (init-param 'contextClass') overrides the
	 * default {@code XmlWebApplicationContext} and allows for specifying an alternative class,
	 * such as {@code AnnotationConfigWebApplicationContext}.
	 * <p>Calling {@link #setContextInitializerClasses} (init-param 'contextInitializerClasses')
	 * indicates which {@code ApplicationContextInitializer} classes should be used to
	 * further configure the internal application context prior to refresh().
	 * @see #DispatcherServlet(WebApplicationContext)
	 */
	public DispatcherServlet() {
		super();
		setDispatchOptionsRequest(true);
	}

	/**
	 * Create a new {@code DispatcherServlet} with the given web application context. This
	 * constructor is useful in Servlet 3.0+ environments where instance-based registration
	 * of servlets is possible through the {@link ServletContext#addServlet} API.
	 * <p>Using this constructor indicates that the following properties / init-params
	 * will be ignored:
	 * <ul>
	 * <li>{@link #setContextClass(Class)} / 'contextClass'</li>
	 * <li>{@link #setContextConfigLocation(String)} / 'contextConfigLocation'</li>
	 * <li>{@link #setContextAttribute(String)} / 'contextAttribute'</li>
	 * <li>{@link #setNamespace(String)} / 'namespace'</li>
	 * </ul>
	 * <p>The given web application context may or may not yet be {@linkplain
	 * ConfigurableApplicationContext#refresh() refreshed}. If it has <strong>not</strong>
	 * already been refreshed (the recommended approach), then the following will occur:
	 * <ul>
	 * <li>If the given context does not already have a {@linkplain
	 * ConfigurableApplicationContext#setParent parent}, the root application context
	 * will be set as the parent.</li>
	 * <li>If the given context has not already been assigned an {@linkplain
	 * ConfigurableApplicationContext#setId id}, one will be assigned to it</li>
	 * <li>{@code ServletContext} and {@code ServletConfig} objects will be delegated to
	 * the application context</li>
	 * <li>{@link #postProcessWebApplicationContext} will be called</li>
	 * <li>Any {@code ApplicationContextInitializer}s specified through the
	 * "contextInitializerClasses" init-param or through the {@link
	 * #setContextInitializers} property will be applied.</li>
	 * <li>{@link ConfigurableApplicationContext#refresh refresh()} will be called if the
	 * context implements {@link ConfigurableApplicationContext}</li>
	 * </ul>
	 * If the context has already been refreshed, none of the above will occur, under the
	 * assumption that the user has performed these actions (or not) per their specific
	 * needs.
	 * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
	 * @param webApplicationContext the context to use
	 * @see #initWebApplicationContext
	 * @see #configureAndRefreshWebApplicationContext
	 * @see org.springframework.web.WebApplicationInitializer
	 */
	public DispatcherServlet(WebApplicationContext webApplicationContext) {
		super(webApplicationContext);
		setDispatchOptionsRequest(true);
	}


	/**
	 * Set whether to detect all HandlerMapping beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerMapping" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerMapping, despite multiple HandlerMapping beans being defined in the context.
	 */
	public void setDetectAllHandlerMappings(boolean detectAllHandlerMappings) {
		this.detectAllHandlerMappings = detectAllHandlerMappings;
	}

	/**
	 * Set whether to detect all HandlerAdapter beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerAdapter" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerAdapter, despite multiple HandlerAdapter beans being defined in the context.
	 */
	public void setDetectAllHandlerAdapters(boolean detectAllHandlerAdapters) {
		this.detectAllHandlerAdapters = detectAllHandlerAdapters;
	}

	/**
	 * Set whether to detect all HandlerExceptionResolver beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerExceptionResolver" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerExceptionResolver, despite multiple HandlerExceptionResolver beans being defined in the context.
	 */
	public void setDetectAllHandlerExceptionResolvers(boolean detectAllHandlerExceptionResolvers) {
		this.detectAllHandlerExceptionResolvers = detectAllHandlerExceptionResolvers;
	}

	/**
	 * Set whether to detect all ViewResolver beans in this servlet's context. Otherwise,
	 * just a single bean with name "viewResolver" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * ViewResolver, despite multiple ViewResolver beans being defined in the context.
	 */
	public void setDetectAllViewResolvers(boolean detectAllViewResolvers) {
		this.detectAllViewResolvers = detectAllViewResolvers;
	}

	/**
	 * Set whether to throw a NoHandlerFoundException when no Handler was found for this request.
	 * This exception can then be caught with a HandlerExceptionResolver or an
	 * {@code @ExceptionHandler} controller method.
	 * <p>Note that if {@link org.springframework.web.servlet.resource.DefaultServletHttpRequestHandler}
	 * is used, then requests will always be forwarded to the default servlet and a
	 * NoHandlerFoundException would never be thrown in that case.
	 * <p>Default is "false", meaning the DispatcherServlet sends a NOT_FOUND error through the
	 * Servlet response.
	 * @since 4.0
	 */
	public void setThrowExceptionIfNoHandlerFound(boolean throwExceptionIfNoHandlerFound) {
		this.throwExceptionIfNoHandlerFound = throwExceptionIfNoHandlerFound;
	}

	/**
	 * Set whether to perform cleanup of request attributes after an include request, that is,
	 * whether to reset the original state of all request attributes after the DispatcherServlet
	 * has processed within an include request. Otherwise, just the DispatcherServlet's own
	 * request attributes will be reset, but not model attributes for JSPs or special attributes
	 * set by views (for example, JSTL's).
	 * <p>Default is "true", which is strongly recommended. Views should not rely on request attributes
	 * having been set by (dynamic) includes. This allows JSP views rendered by an included controller
	 * to use any model attributes, even with the same names as in the main JSP, without causing side
	 * effects. Only turn this off for special needs, for example to deliberately allow main JSPs to
	 * access attributes from JSP views rendered by an included controller.
	 */
	public void setCleanupAfterInclude(boolean cleanupAfterInclude) {
		this.cleanupAfterInclude = cleanupAfterInclude;
	}


	/**
	 * This implementation calls {@link #initStrategies}.
	 */
	@Override
	protected void onRefresh(ApplicationContext context) {
		initStrategies(context);
	}

	/**
	 * Initialize the strategy objects that this servlet uses.
	 * <p>May be overridden in subclasses in order to initialize further strategy objects.
	 *
	 * @tips 初始化 SpringMVC 的组件
	 */
	protected void initStrategies(ApplicationContext context) {
		initMultipartResolver(context);
		initLocaleResolver(context);
		initThemeResolver(context);
		initHandlerMappings(context);
		initHandlerAdapters(context);
		initHandlerExceptionResolvers(context);
		initRequestToViewNameTranslator(context);
		initViewResolvers(context);
		initFlashMapManager(context);
	}

	/**
	 * Initialize the MultipartResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * no multipart handling is provided.
	 *
	 * @tips 在 Spring 中，MultipartResolver 主要用来处理文件上传，默认情况下，Spring是没有 ，multipart处理的，
	 * 因为一些开发者想要自己处理它们，如果想使用 Spring的 multipart ，则需要在 Web 应用的上下文中添加 multipart 解析器。
	 * 这样，每个请求就会被检查是否包含 multipart。然而，如果请求中包含 multipart，那么上下文中定义的 MultipartResolver 就会解析它，
	 * 这样请求中的 multipart 属性 就会像其它属性一样被处理 ，常用配置如下：
	 * <bean id="multipartResolver" class="org.springframework.web.multipart.commons.CommonsMultipartResolver" >
	 *     该属性用来配置可传文件的最大字节数
	 *     <property name="maximumFileSize" >
	 *         <value>100000</value>
	 *     </property>
	 * </bean>
	 *
	 * 内容类型( Content-Type )为 multipart/* 的请求的解析器接口。
	 */
	private void initMultipartResolver(ApplicationContext context) {
		try {
			// 获得 MULTIPART_RESOLVER_BEAN_NAME 对应的 MultipartResolver Bean 对象
			// 默认情况下，multipartResolver 对应的是 StandardServletMultipartResolver 类的 Bean 对象。
			this.multipartResolver = context.getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.multipartResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.multipartResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// Default is no multipart resolver.
			this.multipartResolver = null;
			if (logger.isTraceEnabled()) {
				logger.trace("No MultipartResolver '" + MULTIPART_RESOLVER_BEAN_NAME + "' declared");
			}
		}
	}

	/**
	 * Initialize the LocaleResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to AcceptHeaderLocaleResolver.
	 *
	 * @tips 在 Spring 的国际化配置中一共有3中使用方式
	 * 1、基于 URL 参数的配置
	 * 		通过 URL 参数来控制国际化，比如在页面加一句 <a href="?locale=zh_CN“>简体中文</a> 来控制
	 * 		项目中使用呢的国际化参数，而提供这个功能的就是 AcceptHeaderLocaleResolver，默认的参数名 为 locale，注意大小写
	 *
	 * 2、基于 session 的配置
	 * 		它会检验用户回话中预置的属性来解析区域。最常用的是根据用户会话过程中的语言设定巨鼎语言种类，
	 * 		如果该会话属性不存在，它会根据 accept-language HTTP 头部确定默认区域
	 *
	 * 3、基于 cookie 的国际化配置
	 * 		CookieLocaleResolver 用于通过浏览器的 cookie 设置取得 Locale 对象。这种策略在应用程序不支持会话或者
	 * 		状态必须保存在客户端时有用
	 */
	private void initLocaleResolver(ApplicationContext context) {
		try {
			this.localeResolver = context.getBean(LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.localeResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.localeResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.localeResolver = getDefaultStrategy(context, LocaleResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No LocaleResolver '" + LOCALE_RESOLVER_BEAN_NAME +
						"': using default [" + this.localeResolver.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the ThemeResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to a FixedThemeResolver.
	 *
	 * @tips 在 Web 开发中经常会遇到通过主题 Theme 来控制网页风格，浙江进一步改善用户体验。简单地说，一个主题就是一组静态
	 * 资源（比如样式和图片），它们可以影响用户程序的视觉效果。Spring 中 主题功能和国际化功能非常类似。
	 *
	 * 主题资源：
	 * 		org.springframework.ui.context.ThemeSource 是 Spring 中主题资源的接口，Spring 的主题需要通过 ThemeSource 接口
	 * 		来实现存放主题信息的资源。
	 * 		org.springframework.ui.context.support.ResourceBundleThemeSource 是ThemeSource 接口默认实现类（也就是
	 * 	    通过 ResourceBundle 资源的方式定义主题），在 Spring 中配置如下
	 * 	    <bean id="themeSource" class="org.springframework.ui.context.support.ResourceBundleThemeSource" >
	 * 	        <property name="basenamePrefix" value="com.test. " >
	 *
	 * 	        </property>
	 * 	     /bean>
	 * 	     默认状态下是在类路径根目录下查找相应的资源文件，也可以通过 basenamePrefix 来制定。
	 * 	     这样，DispatcherServlet 就会在com.test 包下查找资源文件
	 *
	 * 主题解析器
	 * 		ThemeSource 定义了一些主题资源，那么不同的用户使用什么主题资源由谁定义呢？
	 * 		org.springframework.web.servlet.ThemeResolver 是主题解析器的接口，主题解析的工作便由它的子类来完成
	 * 		对于主题解析器的子类主要有3个比较常见的实现。以主题文件 summer.properties 为例
	 * 		（1）FixedThemeResolver 用于选择一个固定的主题。
	 * 		（2）CookieThemeResolver 用于实现用户所选的主题，以 cookie 的形式存放在客户端的机器上。
	 * 		（3）SessionThemeResolver 用于主题保存在用户的 HTTP Session 中，保存在 HttpSession 中。
	 * 		（4）AbstractThemeResolver 是一个抽象类 被 SessionThemeResolver 和 FixedThemeResolver 继承，
	 * 			用户也可以继承它来自定义主题解析器。
	 *
	 * 如果需要根据用户请求来改变主题，那么 Spring 提供了一个已经实现的拦截器——ThemeChangeInterceptor 拦截器
	 */
	private void initThemeResolver(ApplicationContext context) {
		try {
			this.themeResolver = context.getBean(THEME_RESOLVER_BEAN_NAME, ThemeResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.themeResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.themeResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.themeResolver = getDefaultStrategy(context, ThemeResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No ThemeResolver '" + THEME_RESOLVER_BEAN_NAME +
						"': using default [" + this.themeResolver.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the HandlerMappings used by this class.
	 * <p>If no HandlerMapping beans are defined in the BeanFactory for this namespace,
	 * we default to BeanNameUrlHandlerMapping.
	 *
	 * @tips 当客户端发出 Request 时 DispatcherServlet 会将 Request 提交 HandlerMapping，然后 HandlerMapping
	 * 根据 Web Application Context 的配置来回传给 DispatcherServlet 相应的Controller。
	 * 在基于 SpringMVC 的 Web 应用程序中，我们可以为 Dispatcher 提供多个 HandlerMapping供其使用。DispatchServlet 在选用
	 * HandlerMapping 的过程中，将根据我们所指定的一系列 HandlerMapping的优先级进行排序，然后优先使用优先级在前的HandlerMapping。
	 *
	 * 默认情况下SpringMVC 将加载当前系统中所有实现了 HandlerMapping 接口的 bean。如果只期望 SpringMVC加载指定的 HandlerMapping时，
	 * 可以修改 web.xml 中的 DispatcherServlet的初始化参数，将 detectAllHandlerMappings 的值设置为 false
	 *
	 * 此时，SpringMVC 将查找名为 “handlerMapping” 的bean，并作为当前系统中唯一的 handlermapping。如果没有定义 handlerMapping的话，
	 * 则SpringMVC 将按照 org.springframework.web.servlet.Dispatcher 所在目录下的 DispatcherServlet.properties 中所定义的 org.springframework.web.servlet.HandlerMapping 的内容
	 * 拉加载默认的 handlerMapping（用户没有自定义 Strategies的情况下）
	 */
	private void initHandlerMappings(ApplicationContext context) {
		// 置空 handlerMappings
		this.handlerMappings = null;

		// <1> 如果开启探测功能，则扫描已注册的 HandlerMapping 的 Bean 们，添加到 handlerMappings 中
		if (this.detectAllHandlerMappings) {
			// Find all HandlerMappings in the ApplicationContext, including ancestor contexts.
			// 扫描已注册的 HandlerMapping 的 Bean 们
			Map<String, HandlerMapping> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);
			// 添加到 handlerMappings 中，并进行排序
			if (!matchingBeans.isEmpty()) {
				this.handlerMappings = new ArrayList<>(matchingBeans.values());
				// We keep HandlerMappings in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerMappings);
			}
		}
		// <2> 如果关闭探测功能，则获得 HANDLER_MAPPING_BEAN_NAME 对应的 Bean 对象，并设置为 handlerMappings
		else {
			try {
				HandlerMapping hm = context.getBean(HANDLER_MAPPING_BEAN_NAME, HandlerMapping.class);
				this.handlerMappings = Collections.singletonList(hm);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerMapping later.
			}
		}

		// Ensure we have at least one HandlerMapping, by registering
		// a default HandlerMapping if no other mappings are found.
		if (this.handlerMappings == null) {
			// <3> 如果未获得到，则获得默认配置的 HandlerMapping 类
			this.handlerMappings = getDefaultStrategies(context, HandlerMapping.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerMappings declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the HandlerAdapters used by this class.
	 * <p>If no HandlerAdapter beans are defined in the BeanFactory for this namespace,
	 * we default to SimpleControllerHandlerAdapter.
	 *
	 * @tips 从名字也能联想到这是个典型的适配器模式的使用，在计算机编程中，适配器模式将一个类的接口
	 * 适配成用户所期待的。使用适配器，可以使接口不兼容而无法在一起工作的类协同工作，做法是将类自己的接口
	 * 包裹在一个已存在的类中。那么在处理 handler 时为什么会使用适配器模式呢?
	 *
	 * Spring中所使用的 Handler 并没有任何特殊的联系，但是为了统一处理，Spring 提供了
	 * 不同情况下的适配器。
	 *
	 */
	private void initHandlerAdapters(ApplicationContext context) {
		this.handlerAdapters = null;

		/**
		 * detectAllHandlerAdapters变量和 detectAllHandlerMappings类似，只不过作用对象为 handlerAdapter。
		 */
		if (this.detectAllHandlerAdapters) {
			// Find all HandlerAdapters in the ApplicationContext, including ancestor contexts.
			Map<String, HandlerAdapter> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerAdapter.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerAdapters = new ArrayList<>(matchingBeans.values());
				// We keep HandlerAdapters in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerAdapters);
			}
		}
		else {
			try {
				HandlerAdapter ha = context.getBean(HANDLER_ADAPTER_BEAN_NAME, HandlerAdapter.class);
				this.handlerAdapters = Collections.singletonList(ha);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerAdapter later.
			}
		}

		// Ensure we have at least some HandlerAdapters, by registering
		// default HandlerAdapters if no other adapters are found.
		if (this.handlerAdapters == null) {
			// 如果无法找到对应的 bean，那么系统会尝试加载默认的适配器。
			this.handlerAdapters = getDefaultStrategies(context, HandlerAdapter.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerAdapters declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the HandlerExceptionResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to no exception resolver.
	 *
	 * @tips 基于 HandlerExceptionResolver 接口的异常处理，使用这种方式只需要实现 resolverException 方法，该方法返回
	 * 一个 ModelAndView 对象，在方法内部对异常的类型进行判断，然后尝试生成对应的 ModelAndView 对象，如果该方法返回了 null，
	 * 则 Spring 会继续寻找其它的实现了 HandlerExceptionResolver 接口的 bean，换句话说，会逐个执行 逐个类型的bean，直到返回
	 * 一个ModelAndView 对象。
	 */
	private void initHandlerExceptionResolvers(ApplicationContext context) {
		// 置空 handlerExceptionResolvers 处理
		this.handlerExceptionResolvers = null;

		// 情况一，自动扫描 HandlerExceptionResolver 类型的 Bean 们
		/**
		 * 默认情况下，detectAllHandlerExceptionResolvers 为 true ，所以走情况一的逻辑，
		 * 自动扫描 HandlerExceptionResolver 类型的 Bean 们。
		 */
		if (this.detectAllHandlerExceptionResolvers) {
			// Find all HandlerExceptionResolvers in the ApplicationContext, including ancestor contexts.
			Map<String, HandlerExceptionResolver> matchingBeans = BeanFactoryUtils
					.beansOfTypeIncludingAncestors(context, HandlerExceptionResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerExceptionResolvers = new ArrayList<>(matchingBeans.values());
				// We keep HandlerExceptionResolvers in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerExceptionResolvers);
			}
		}
		// 情况二，获得名字为 HANDLER_EXCEPTION_RESOLVER_BEAN_NAME 的 Bean 们
		else {
			try {
				HandlerExceptionResolver her =
						context.getBean(HANDLER_EXCEPTION_RESOLVER_BEAN_NAME, HandlerExceptionResolver.class);
				this.handlerExceptionResolvers = Collections.singletonList(her);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, no HandlerExceptionResolver is fine too.
			}
		}

		// Ensure we have at least some HandlerExceptionResolvers, by registering
		// default HandlerExceptionResolvers if no other resolvers are found.
		// 情况三，如果未获得到，则获得默认配置的 HandlerExceptionResolver 类
		if (this.handlerExceptionResolvers == null) {
			/**
			 * 在默认配置的 Spring Boot 场景下，handlerExceptionResolvers 的结果是：
			 * org.springframework.boot.autoconfigure.web.DefaultErrorAttributes
			 * HandlerExceptionResolverComposite
			 */
			this.handlerExceptionResolvers = getDefaultStrategies(context, HandlerExceptionResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerExceptionResolvers declared in servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the RequestToViewNameTranslator used by this servlet instance.
	 * <p>If no implementation is configured then we default to DefaultRequestToViewNameTranslator.
	 *
	 * @tips 当 Controller 处理器方法没有返回一个 View 对象或逻辑试图名称，并且该方法中没有直接往 response 的输出流里面写数据的时候，
	 * Spring就会采用约定好的方式提供一个逻辑视图名称。这个逻辑视图名称是通过 Spring定义的org.springframework.web.servlet.RequestToViewNameTranslator
	 * 接口的 getViewName 方法实现的，可以自己实现它来约定好没有返回视图名称的时候如何确定视图名称。Spring 已经给我们提供了一个它自己的实现，
	 * 那就是 org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator。
	 *
	 * 支持用户定义的属性
	 * 		prefix：前缀
	 * 		suffix：后置
	 * 		separator：分隔符，默认斜杠 ”/“
	 * 		stripLeadingSlash：如果首字符是分隔符，是否要去除，默认是true
	 * 		stripTrailingSlash：如果最后一个字符是分隔符，是否要去除，默认是true
	 * 		stripExtension：如果请求路径包含扩展名是否要去除，默认是true
	 * 		urlDecode：是否需要对 URL 解码，默认是true，它会采用 request 指定的编码或者
	 * 			ISO-8859-1 编码对 URL 进行解码。
	 */
	private void initRequestToViewNameTranslator(ApplicationContext context) {
		try {
			this.viewNameTranslator =
					context.getBean(REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME, RequestToViewNameTranslator.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.viewNameTranslator.getClass().getSimpleName());
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.viewNameTranslator);
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.viewNameTranslator = getDefaultStrategy(context, RequestToViewNameTranslator.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No RequestToViewNameTranslator '" + REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME +
						"': using default [" + this.viewNameTranslator.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the ViewResolvers used by this class.
	 * <p>If no ViewResolver beans are defined in the BeanFactory for this
	 * namespace, we default to InternalResourceViewResolver.
	 *
	 * @tips 在 SpringMVC 中，当 Controller 将请求处理结果放入到 ModelAndView 中以后，DispatcherServlet会根据 ModelAndView
	 * 选择合适的视图进行渲染。在 ViewResolver 中定义了 resolverViewName 方法，根据viewName 创建合适类型的 view 实现。
	 *
	 */
	private void initViewResolvers(ApplicationContext context) {
		// 置空 viewResolvers 处理
		this.viewResolvers = null;

		/**
		 * 默认情况下，detectAllViewResolvers 为 true ，所以走情况一的逻辑，自动扫描 ViewResolver 类型的 Bean 们。
		 * 在默认配置的 Spring Boot 场景下，viewResolvers 的结果是：
		 *
		 * ContentNegotiatingViewResolver
		 * BeanNameViewResolver
		 * ThymeleafViewResolver
		 * ViewResolverComposite
		 * InternalResourceViewResolver
		 */
		// 情况一，自动扫描 ViewResolver 类型的 Bean 们
		if (this.detectAllViewResolvers) {
			// Find all ViewResolvers in the ApplicationContext, including ancestor contexts.
			Map<String, ViewResolver> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, ViewResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.viewResolvers = new ArrayList<>(matchingBeans.values());
				// We keep ViewResolvers in sorted order.
				AnnotationAwareOrderComparator.sort(this.viewResolvers);
			}
		}
		// 情况二，获得名字为 VIEW_RESOLVER_BEAN_NAME 的 Bean 们
		else {
			try {
				ViewResolver vr = context.getBean(VIEW_RESOLVER_BEAN_NAME, ViewResolver.class);
				this.viewResolvers = Collections.singletonList(vr);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default ViewResolver later.
			}
		}

		// Ensure we have at least one ViewResolver, by registering
		// a default ViewResolver if no other resolvers are found.
		// 情况三，如果未获得到，则获得默认配置的 ViewResolver 类
		if (this.viewResolvers == null) {
			this.viewResolvers = getDefaultStrategies(context, ViewResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No ViewResolvers declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the {@link FlashMapManager} used by this servlet instance.
	 * <p>If no implementation is configured then we default to
	 * {@code org.springframework.web.servlet.support.DefaultFlashMapManager}.
	 *
	 * @tips SpringMVC Flash attributes 提供了一个请求存储属性，可供其它请求使用。在使用重定向时候非常必要，
	 * 例如 Post/Redirect/Get 模式。Flash attributes 在重定向之前暂存（就像存在session中）以便重定向之后
	 * 还能使用，并立即删除。
	 * SpringMVC 有两个主要的抽象来支持 flash arrtibutes，FlashMap 用于保持 flash attributes，而 FlashMapManager
	 * 用于存储、检索、管理 FlashMap实例。
	 * flash attribute 支持默认开启“on”并不需要显示启用，它永远不会导致 HTTP Session 的创建。
	 * 这两个 FlashMap实例都可以通过静态方法 RequestContextUtils 从 SpringMVC 的任何位置访问。
	 */
	private void initFlashMapManager(ApplicationContext context) {
		try {
			this.flashMapManager = context.getBean(FLASH_MAP_MANAGER_BEAN_NAME, FlashMapManager.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.flashMapManager.getClass().getSimpleName());
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.flashMapManager);
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.flashMapManager = getDefaultStrategy(context, FlashMapManager.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No FlashMapManager '" + FLASH_MAP_MANAGER_BEAN_NAME +
						"': using default [" + this.flashMapManager.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Return this servlet's ThemeSource, if any; else return {@code null}.
	 * <p>Default is to return the WebApplicationContext as ThemeSource,
	 * provided that it implements the ThemeSource interface.
	 * @return the ThemeSource, if any
	 * @see #getWebApplicationContext()
	 */
	@Nullable
	public final ThemeSource getThemeSource() {
		return (getWebApplicationContext() instanceof ThemeSource ? (ThemeSource) getWebApplicationContext() : null);
	}

	/**
	 * Obtain this servlet's MultipartResolver, if any.
	 * @return the MultipartResolver used by this servlet, or {@code null} if none
	 * (indicating that no multipart support is available)
	 */
	@Nullable
	public final MultipartResolver getMultipartResolver() {
		return this.multipartResolver;
	}

	/**
	 * Return the configured {@link HandlerMapping} beans that were detected by
	 * type in the {@link WebApplicationContext} or initialized based on the
	 * default set of strategies from {@literal DispatcherServlet.properties}.
	 * <p><strong>Note:</strong> This method may return {@code null} if invoked
	 * prior to {@link #onRefresh(ApplicationContext)}.
	 * @return an immutable list with the configured mappings, or {@code null}
	 * if not initialized yet
	 * @since 5.0
	 */
	@Nullable
	public final List<HandlerMapping> getHandlerMappings() {
		return (this.handlerMappings != null ? Collections.unmodifiableList(this.handlerMappings) : null);
	}

	/**
	 * Return the default strategy object for the given strategy interface.
	 * <p>The default implementation delegates to {@link #getDefaultStrategies},
	 * expecting a single object in the list.
	 * @param context the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the corresponding strategy object
	 * @see #getDefaultStrategies
	 */
	protected <T> T getDefaultStrategy(ApplicationContext context, Class<T> strategyInterface) {
		List<T> strategies = getDefaultStrategies(context, strategyInterface);
		if (strategies.size() != 1) {
			throw new BeanInitializationException(
					"DispatcherServlet needs exactly 1 strategy for interface [" + strategyInterface.getName() + "]");
		}
		return strategies.get(0);
	}

	/**
	 * Create a List of default strategy objects for the given strategy interface.
	 * <p>The default implementation uses the "DispatcherServlet.properties" file (in the same
	 * package as the DispatcherServlet class) to determine the class names. It instantiates
	 * the strategy objects through the context's BeanFactory.
	 * @param context the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the List of corresponding strategy objects
	 *
	 * @tips 在getDefaultStrategies 函数中，Spring 会尝试从 dafaultStrategies 中加载对应的 HandlerAdapter 的属性，
	 * 而 dafaultStrategies 是在 static 静态代码块中初始化的
	 *
	 * 实际上，这是个通用的方法，提供给不同的 strategyInterface 接口，获得对应的类型的数组。
	 */
	@SuppressWarnings("unchecked")
	protected <T> List<T> getDefaultStrategies(ApplicationContext context, Class<T> strategyInterface) {
		// <1> 获得 strategyInterface 对应的 value 值
		String key = strategyInterface.getName();
		String value = defaultStrategies.getProperty(key);
		// <2> 创建 value 对应的对象们，并返回
		if (value != null) {
			// 基于 "," 分隔，创建 classNames 数组
			String[] classNames = StringUtils.commaDelimitedListToStringArray(value);
			// 创建 strategyInterface 集合
			List<T> strategies = new ArrayList<>(classNames.length);
			// 遍历 classNames 数组，创建对应的类，添加到 strategyInterface 中
			for (String className : classNames) {
				try {
					// 获得 className 类
					Class<?> clazz = ClassUtils.forName(className, DispatcherServlet.class.getClassLoader());
					// 创建 className 对应的类，并添加到 strategies 中
					Object strategy = createDefaultStrategy(context, clazz);
					strategies.add((T) strategy);
				}
				catch (ClassNotFoundException ex) {
					throw new BeanInitializationException(
							"Could not find DispatcherServlet's default strategy class [" + className +
							"] for interface [" + key + "]", ex);
				}
				catch (LinkageError err) {
					throw new BeanInitializationException(
							"Unresolvable class definition for DispatcherServlet's default strategy class [" +
							className + "] for interface [" + key + "]", err);
				}
			}
			return strategies;
		}
		else {
			return new LinkedList<>();
		}
	}

	/**
	 * Create a default strategy.
	 * <p>The default implementation uses
	 * {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean}.
	 * @param context the current WebApplicationContext
	 * @param clazz the strategy implementation class to instantiate
	 * @return the fully configured strategy instance
	 * @see org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()
	 * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean
	 */
	protected Object createDefaultStrategy(ApplicationContext context, Class<?> clazz) {
		// 通过 Spring IOC 容器，进行创建对象。
		return context.getAutowireCapableBeanFactory().createBean(clazz);
	}


	/**
	 * Exposes the DispatcherServlet-specific request attributes and delegates to {@link #doDispatch}
	 * for the actual dispatching.
	 *
	 * @tips Spring 将已经初始化的功能辅助工具变量，比如 localeResolver、themeResolver等设置在 request属性中，
	 * 而这些属性会在接下来的处理中派上用场
	 */
	@Override
	protected void doService(HttpServletRequest request, HttpServletResponse response) throws Exception {
		// <1> 打印请求日志，并且日志级别为 DEBUG
		logRequest(request);

		// Keep a snapshot of the request attributes in case of an include,
		// to be able to restore the original attributes after the include.
		// 1003 Asyn
		Map<String, Object> attributesSnapshot = null;
		if (WebUtils.isIncludeRequest(request)) {
			attributesSnapshot = new HashMap<>();
			Enumeration<?> attrNames = request.getAttributeNames();
			while (attrNames.hasMoreElements()) {
				String attrName = (String) attrNames.nextElement();
				if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
					attributesSnapshot.put(attrName, request.getAttribute(attrName));
				}
			}
		}

		// Make framework objects available to handlers and view objects.
		// <3> 设置 Spring 框架中的常用对象到 request 属性中
		request.setAttribute(WEB_APPLICATION_CONTEXT_ATTRIBUTE, getWebApplicationContext());
		request.setAttribute(LOCALE_RESOLVER_ATTRIBUTE, this.localeResolver);
		request.setAttribute(THEME_RESOLVER_ATTRIBUTE, this.themeResolver);
		request.setAttribute(THEME_SOURCE_ATTRIBUTE, getThemeSource());

		// <4>  flashMapManager 1001 Locale + 1002 RequestAttributes
		if (this.flashMapManager != null) {
			FlashMap inputFlashMap = this.flashMapManager.retrieveAndUpdate(request, response);
			if (inputFlashMap != null) {
				request.setAttribute(INPUT_FLASH_MAP_ATTRIBUTE, Collections.unmodifiableMap(inputFlashMap));
			}
			request.setAttribute(OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
			request.setAttribute(FLASH_MAP_MANAGER_ATTRIBUTE, this.flashMapManager);
		}

		try {
			// <5> 执行请求的分发
			doDispatch(request, response);
		}
		finally {
			// <6> 1001 Locale + 1002 RequestAttributes
			if (!WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
				// Restore the original attribute snapshot, in case of an include.
				if (attributesSnapshot != null) {
					restoreAttributesAfterInclude(request, attributesSnapshot);
				}
			}
		}
	}

	private void logRequest(HttpServletRequest request) {
		LogFormatUtils.traceDebug(logger, traceOn -> {
			String params;
			if (isEnableLoggingRequestDetails()) {
				params = request.getParameterMap().entrySet().stream()
						.map(entry -> entry.getKey() + ":" + Arrays.toString(entry.getValue()))
						.collect(Collectors.joining(", "));
			}
			else {
				params = (request.getParameterMap().isEmpty() ? "" : "masked");
			}

			String queryString = request.getQueryString();
			String queryClause = (StringUtils.hasLength(queryString) ? "?" + queryString : "");
			String dispatchType = (!request.getDispatcherType().equals(DispatcherType.REQUEST) ?
					"\"" + request.getDispatcherType().name() + "\" dispatch for " : "");
			String message = (dispatchType + request.getMethod() + " \"" + getRequestUri(request) +
					queryClause + "\", parameters={" + params + "}");

			if (traceOn) {
				List<String> values = Collections.list(request.getHeaderNames());
				String headers = values.size() > 0 ? "masked" : "";
				if (isEnableLoggingRequestDetails()) {
					headers = values.stream().map(name -> name + ":" + Collections.list(request.getHeaders(name)))
							.collect(Collectors.joining(", "));
				}
				return message + ", headers={" + headers + "} in DispatcherServlet '" + getServletName() + "'";
			}
			else {
				return message;
			}
		});
	}

	/**
	 * Process the actual dispatching to the handler.
	 * <p>The handler will be obtained by applying the servlet's HandlerMappings in order.
	 * The HandlerAdapter will be obtained by querying the servlet's installed HandlerAdapters
	 * to find the first that supports the handler class.
	 * <p>All HTTP methods are handled by this method. It's up to HandlerAdapters or handlers
	 * themselves to decide which methods are acceptable.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception in case of any kind of processing failure
	 */
	protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
		HttpServletRequest processedRequest = request;
		HandlerExecutionChain mappedHandler = null;
		boolean multipartRequestParsed = false;

		// <1>1003 Async
		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);

		try {
			ModelAndView mv = null;
			Exception dispatchException = null;

			try {
				/**
				 * 如果是 MultipartContent类型的 request 则转换 request 为 MultipartHttpServletRequest 类型的 request
				 */
				// 检查是否是上传请求。如果是，则封装成 MultipartHttpServletRequest 对象。
				processedRequest = checkMultipart(request);
				multipartRequestParsed = (processedRequest != request);

				// Determine handler for the current request.
				// 根据 request 信息寻找对应的 Handler 其实是HandlerExecutionChain
				// <3> 获得请求对应的 HandlerExecutionChain 对象 它包含处理器( handler )和拦截器们( HandlerInterceptor 数组 )。
				mappedHandler = getHandler(processedRequest);
				if (mappedHandler == null) {
					// 如果没有找到对应的 handler 则通过 response 反馈错误信息
					// <3.1> 如果获取不到，则根据配置抛出异常或返回 404 错误
					noHandlerFound(processedRequest, response);
					return;
				}

				// Determine handler adapter for the current request.
				// 根据当前的 handler 寻找对应的 HandlerAdapter 对象 （普通的 Web 请求会交给 SimpleControllerHandlerAdapter）
				HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

				// Process last-modified header, if supported by the handler.
				// 如果当前 handler 支持 last-modified 头处理
				// <4.1> TODO 芋艿 last-modified
				String method = request.getMethod();
				boolean isGet = "GET".equals(method);
				if (isGet || "HEAD".equals(method)) {
					/**
					 * 在研究 Spring 对缓存处理的功能支持前，我们先了解一个概念：Last-Modified缓存机制。
					 * 1、在客户端第一次输入 URL 时，服务器端会返回内容和状态码200，表示请求成功，同时会添加一个“Last-Modified”的
					 * 		响应头，表示此文件在服务器的最后更新时间，例如：Last-Modified: Wed, 14 Mar 2012 10:22:42 GMT
					 * 2、客户端第二次请求此 URL 时，客户端会向服务器发送请求头 “If-Modified-Since”，询问服务器该时间之后当前的请求内容
					 * 是否有被修改过，如果请求内容没有变化，则自动返回 HTTP 304 状态码（只要响应头，内容为空，这样就节省了网络带宽）
					 *
					 * Spring 提供了对 Last-Modified 机制的支持，只需要实现 LastModified接口
					 */
					long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
					if (new ServletWebRequest(request, response).checkNotModified(lastModified) && isGet) {
						return;
					}
				}

				// 拦截器的 preHandler 方法的调用
				/**
				 * Servlet API 定义的 servlet 过滤器可以在 servlet 处理每个Web请求的前后分别对它进行前置处理和后置处理。
				 * 此外，有些时候，你可能只想处理由某些 SpringMVC 处理程序处理的 Web请求，并在这些处理程序返回的模型属性被传递
				 * 到视图之前，对它们进行一些操作
				 * SpringMVC 允许通过处理拦截 Web 请求，进行前置处理和后置处理，处理拦截是在Spring的 Web应用程序上下文中配置的，
				 * 因此它们可以利用各种容器特性，并引用容器中声明的任何 bean，处理拦截是针对特殊的处理程序映射进行注册的，因此它
				 * 只拦截通过这些处理程序映射的请求。每个处理拦截都必须实现 HandlerInterceptor接口，它包含三个需要实现的回调方法：
				 * preHandle、postHandle和afterCompletion，第二个方法还允许访问返回的 ModelAndView对象，最后一个方法是在所有请求
				 * 处理完成之后被调用的
				 */
				// <5> 前置处理 拦截器
				if (!mappedHandler.applyPreHandle(processedRequest, response)) {
					return;
				}

				// Actually invoke the handler.
				// 真正的激活 handler 并返回视图
				// 通过适配器中转调用Handler并返回视图的
				// 这里，一般就会调用我们定义的 Controller 的方法
				mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

				// <7> 1003 Asyn
				if (asyncManager.isConcurrentHandlingStarted()) {
					return;
				}

				// 视图名称转换应用于需要添加前缀后缀的情况
				// 当无视图的情况下，设置默认视图
				applyDefaultViewName(processedRequest, mv);
				// 应用所有拦截器的 postHandle 方法
				// <9> 后置处理 拦截器
				mappedHandler.applyPostHandle(processedRequest, response, mv);
			}
			catch (Exception ex) {
				// <10> 记录异常 注意，此处仅仅记录，不会抛出异常，而是统一交给 <11> 处理。
				dispatchException = ex;
			}
			catch (Throwable err) {
				// As of 4.3, we're processing Errors thrown from handler methods as well,
				// making them available for @ExceptionHandler methods and other scenarios.
				dispatchException = new NestedServletException("Handler dispatch failed", err);
			}
			// <11> 处理正常和异常的请求调用结果。 注意，正常的、异常的，都会进行处理。
			processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
		}
		catch (Exception ex) {
			// <12> 已完成 拦截器 拦截器的已完成处理，即调用该方法
			triggerAfterCompletion(processedRequest, response, mappedHandler, ex);
		}
		catch (Throwable err) {
			// <12> 已完成 拦截器
			triggerAfterCompletion(processedRequest, response, mappedHandler,
					new NestedServletException("Handler processing failed", err));
		}
		finally {
			// <13.1> 1003 Asyn
			if (asyncManager.isConcurrentHandlingStarted()) {
				// Instead of postHandle and afterCompletion
				if (mappedHandler != null) {
					mappedHandler.applyAfterConcurrentHandlingStarted(processedRequest, response);
				}
			}
			else {
				// Clean up any resources used by a multipart request.
				// 如果是上传请求，则调用 #cleanupMultipart 方法，清理资源。
				if (multipartRequestParsed) {
					cleanupMultipart(processedRequest);
				}
			}
		}
	}

	/**
	 * Do we need view name translation?
	 */
	private void applyDefaultViewName(HttpServletRequest request, @Nullable ModelAndView mv) throws Exception {
		if (mv != null && !mv.hasView()) { // 无视图
			// 获得默认视图
			String defaultViewName = getDefaultViewName(request);
			// 设置默认视图
			if (defaultViewName != null) {
				mv.setViewName(defaultViewName);
			}
		}
	}

	/**
	 * Handle the result of handler selection and handler invocation, which is
	 * either a ModelAndView or an Exception to be resolved to a ModelAndView.
	 *
	 * @tips 处理正常和异常的请求调用结果。
	 */
	private void processDispatchResult(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerExecutionChain mappedHandler, @Nullable ModelAndView mv,
			@Nullable Exception exception) throws Exception {

		// <1> 标记，是否是生成的 ModelAndView 对象
		boolean errorView = false;

		// <2> 如果是否异常的结果
		if (exception != null) {
			// 情况一，从 ModelAndViewDefiningException 中获得 ModelAndView 对象
			if (exception instanceof ModelAndViewDefiningException) {
				logger.debug("ModelAndViewDefiningException encountered", exception);
				mv = ((ModelAndViewDefiningException) exception).getModelAndView();
			}
			// 情况二，处理异常，生成 ModelAndView 对象
			else {
				Object handler = (mappedHandler != null ? mappedHandler.getHandler() : null);
				// 异常视图的处理
				mv = processHandlerException(request, response, handler, exception);
				// 标记 errorView
				errorView = (mv != null);
			}
		}

		// Did the handler return a view to render?
		// 如果在 Handler 实例的处理中返回了 view 那么需要做页面的处理
		if (mv != null && !mv.wasCleared()) {
			// 处理页面跳转
			// <3.1> 渲染页面
			render(mv, request, response);
			// <3.2> 清理请求中的错误消息属性
			if (errorView) {
				// #processHandlerException 里面 有方法 WebUtils#exposeErrorRequestAttributes
				// 清理请求中的错误消息属性。
				WebUtils.clearErrorRequestAttributes(request);
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("No view rendering, null ModelAndView returned.");
			}
		}

		// <4> TODO 芋艿
		if (WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
			// Concurrent handling started during a forward
			return;
		}

		// <5> 已完成处理 拦截器
		if (mappedHandler != null) {
			// 完成处理激活触发器
			mappedHandler.triggerAfterCompletion(request, response, null);
		}
	}

	/**
	 * Build a LocaleContext for the given request, exposing the request's primary locale as current locale.
	 * <p>The default implementation uses the dispatcher's LocaleResolver to obtain the current locale,
	 * which might change during a request.
	 * @param request current HTTP request
	 * @return the corresponding LocaleContext
	 */
	@Override
	protected LocaleContext buildLocaleContext(final HttpServletRequest request) {
		LocaleResolver lr = this.localeResolver;
		if (lr instanceof LocaleContextResolver) {
			return ((LocaleContextResolver) lr).resolveLocaleContext(request);
		}
		else {
			return () -> (lr != null ? lr.resolveLocale(request) : request.getLocale());
		}
	}

	/**
	 * Convert the request into a multipart request, and make multipart resolver available.
	 * <p>If no multipart resolver is set, simply use the existing request.
	 * @param request current HTTP request
	 * @return the processed request (multipart wrapper if necessary)
	 * @see MultipartResolver#resolveMultipart
	 */
	protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
		if (this.multipartResolver != null && this.multipartResolver.isMultipart(request)) {
			if (WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class) != null) {
				if (request.getDispatcherType().equals(DispatcherType.REQUEST)) {
					logger.trace("Request already resolved to MultipartHttpServletRequest, e.g. by MultipartFilter");
				}
			}
			else if (hasMultipartException(request)) {
				logger.debug("Multipart resolution previously failed for current request - " +
						"skipping re-resolution for undisturbed error rendering");
			}
			else {
				try {
					return this.multipartResolver.resolveMultipart(request);
				}
				catch (MultipartException ex) {
					if (request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) != null) {
						logger.debug("Multipart resolution failed for error dispatch", ex);
						// Keep processing error dispatch with regular request handle below
					}
					else {
						throw ex;
					}
				}
			}
		}
		// If not returned before: return original request.
		return request;
	}

	/**
	 * Check "javax.servlet.error.exception" attribute for a multipart exception.
	 */
	private boolean hasMultipartException(HttpServletRequest request) {
		Throwable error = (Throwable) request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE);
		while (error != null) {
			if (error instanceof MultipartException) {
				return true;
			}
			error = error.getCause();
		}
		return false;
	}

	/**
	 * Clean up any resources used by the given multipart request (if any).
	 * @param request current HTTP request
	 * @see MultipartResolver#cleanupMultipart
	 */
	protected void cleanupMultipart(HttpServletRequest request) {
		if (this.multipartResolver != null) {
			MultipartHttpServletRequest multipartRequest =
					WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
			if (multipartRequest != null) {
				this.multipartResolver.cleanupMultipart(multipartRequest);
			}
		}
	}

	/**
	 * Return the HandlerExecutionChain for this request.
	 * <p>Tries all handler mappings in order.
	 * @param request current HTTP request
	 * @return the HandlerExecutionChain, or {@code null} if no handler could be found
	 *
	 * @tips 在 Spring中最简单的映射处理器配置如下：
	 * <bean id="simpleUrlMapping" class="org.springframework.web.servlet.handler.SimpleUrlHandlerMapping">
	 *     <property name="mapping">
	 *         <props>
	 *             <prop key="/userlist.htm">userController</prop>
	 *         </props>
	 *     </property>
	 * </bean>
	 *
	 * 在 Spring 加载的过程中，Spring 会将类型为 SimpleUrlHandlerMapping 的实例加载到 this.handlerMapping 中，
	 * 按照常理推断，根据 request  提取对应的 Handler，无非就是提取当前实例中 userController，但是userController为继承
	 * 自 AbstractController 类型实例，与HandlerExecutionChain 并无任何关联，那么这一步是如何封装的？
	 */
	@Nullable
	protected HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		if (this.handlerMappings != null) {
			// 遍历 HandlerMapping 数组
			for (HandlerMapping mapping : this.handlerMappings) {
				// 获得请求对应的 HandlerExecutionChain 对象
				HandlerExecutionChain handler = mapping.getHandler(request);
				// 获得到，则返回
				if (handler != null) {
					return handler;
				}
			}
		}
		return null;
	}

	/**
	 * No handler found -> set appropriate HTTP response status.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception if preparing the response failed
	 */
	protected void noHandlerFound(HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (pageNotFoundLogger.isWarnEnabled()) {
			pageNotFoundLogger.warn("No mapping for " + request.getMethod() + " " + getRequestUri(request));
		}
		if (this.throwExceptionIfNoHandlerFound) {
			throw new NoHandlerFoundException(request.getMethod(), getRequestUri(request),
					new ServletServerHttpRequest(request).getHeaders());
		}
		else {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	/**
	 * Return the HandlerAdapter for this handler object.
	 * @param handler the handler object to find an adapter for
	 * @throws ServletException if no HandlerAdapter can be found for the handler. This is a fatal error.
	 */
	protected HandlerAdapter getHandlerAdapter(Object handler) throws ServletException {
		if (this.handlerAdapters != null) {
			// 遍历 HandlerAdapter 数组
			for (HandlerAdapter adapter : this.handlerAdapters) {
				// 判断是否支持当前处理器
				if (adapter.supports(handler)) {
					// 如果支持，则返回
					return adapter;
				}
			}
		}
		// 没找到对应的 HandlerAdapter 对象，抛出 ServletException 异常
		throw new ServletException("No adapter for handler [" + handler +
				"]: The DispatcherServlet configuration needs to include a HandlerAdapter that supports this handler");
	}

	/**
	 * Determine an error ModelAndView via the registered HandlerExceptionResolvers.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or {@code null} if none chosen at the time of the exception
	 * (for example, if multipart resolution failed)
	 * @param ex the exception that got thrown during handler execution
	 * @return a corresponding ModelAndView to forward to
	 * @throws Exception if no error ModelAndView found
	 */
	@Nullable
	protected ModelAndView processHandlerException(HttpServletRequest request, HttpServletResponse response,
			@Nullable Object handler, Exception ex) throws Exception {

		// Success and error responses may use different content types
		// 移除 PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE 属性
		request.removeAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);

		// Check registered HandlerExceptionResolvers...
		// <a> 遍历 HandlerExceptionResolver 数组，解析异常，生成 ModelAndView 对象
		ModelAndView exMv = null;
		if (this.handlerExceptionResolvers != null) {
			// 遍历 HandlerExceptionResolver 数组
			for (HandlerExceptionResolver resolver : this.handlerExceptionResolvers) {
				// 解析异常，生成 ModelAndView 对象
				exMv = resolver.resolveException(request, response, handler, ex);
				// 生成成功，结束循环
				if (exMv != null) {
					break;
				}
			}
		}
		// 情况一，生成了 ModelAndView 对象，进行返回
		if (exMv != null) {
			// ModelAndView 对象为空，则返回 null
			if (exMv.isEmpty()) {
				request.setAttribute(EXCEPTION_ATTRIBUTE, ex);
				return null;
			}
			// We might still need view name translation for a plain error model...
			// 设置默认视图
			if (!exMv.hasView()) {
				String defaultViewName = getDefaultViewName(request);
				if (defaultViewName != null) {
					exMv.setViewName(defaultViewName);
				}
			}
			// 打印日志
			if (logger.isTraceEnabled()) {
				logger.trace("Using resolved error view: " + exMv, ex);
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Using resolved error view: " + exMv);
			}
			// 设置请求中的错误消息属性
			WebUtils.exposeErrorRequestAttributes(request, ex, getServletName());
			return exMv;
		}

		// 情况二，未生成 ModelAndView 对象，则抛出异常
		throw ex;
	}

	/**
	 * Render the given ModelAndView.
	 * <p>This is the last stage in handling a request. It may involve resolving the view by name.
	 * @param mv the ModelAndView to render
	 * @param request current HTTP servlet request
	 * @param response current HTTP servlet response
	 * @throws ServletException if view is missing or cannot be resolved
	 * @throws Exception if there's a problem rendering the view
	 *
	 * @tips 无论是一个系统还是一个站点，最重要的工作都是与用户进行交互，用户操作系统后无论下发的命令成功与否都需要
	 * 给用户一个反馈，以便于用户进行下一步的判断。所以，在逻辑处理的最后一定会涉及一个页面跳转的问题。
	 */
	protected void render(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// Determine locale for request and apply it to the response.
		// <1> TODO 芋艿 从 request 中获得 Locale 对象，并设置到 response 中
		Locale locale =
				(this.localeResolver != null ? this.localeResolver.resolveLocale(request) : request.getLocale());
		response.setLocale(locale);

		// 获得 View 对象
		View view;
		String viewName = mv.getViewName();
		// 情况一，使用 viewName 获得 View 对象
		if (viewName != null) {
			// We need to resolve the view name.
			// 解析视图名称，Dispatcher 会根据 ModelAndView 选择合适的视图来进行渲染
			// <2.1> 使用 viewName 获得 View 对象
			view = resolveViewName(viewName, mv.getModelInternal(), locale, request);
			if (view == null) { // 获取不到，抛出 ServletException 异常
				throw new ServletException("Could not resolve view with name '" + mv.getViewName() +
						"' in servlet with name '" + getServletName() + "'");
			}
		}
		// 情况二，直接使用 ModelAndView 对象的 View 对象
		else {
			// No need to lookup: the ModelAndView object contains the actual View object.
			view = mv.getView();
			if (view == null) {
				throw new ServletException("ModelAndView [" + mv + "] neither contains a view name nor a " +
						"View object in servlet with name '" + getServletName() + "'");
			}
		}

		// Delegate to the View object for rendering.
		// 打印日志
		if (logger.isTraceEnabled()) {
			logger.trace("Rendering view [" + view + "] ");
		}
		try {
			// <3> 设置响应的状态码
			if (mv.getStatus() != null) {
				response.setStatus(mv.getStatus().value());
			}
			// 当通过 viewName解析到对应的View后，就可以进行的处理跳转逻辑了
			// <4> 渲染页面
			view.render(mv.getModelInternal(), request, response);
		}
		catch (Exception ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Error rendering view [" + view + "]", ex);
			}
			throw ex;
		}
	}

	/**
	 * Translate the supplied request into a default view name.
	 * @param request current HTTP servlet request
	 * @return the view name (or {@code null} if no default found)
	 * @throws Exception if view name translation failed
	 */
	@Nullable
	protected String getDefaultViewName(HttpServletRequest request) throws Exception {
		// 从请求中，获得视图
		return (this.viewNameTranslator != null ? this.viewNameTranslator.getViewName(request) : null);
	}

	/**
	 * Resolve the given view name into a View object (to be rendered).
	 * <p>The default implementations asks all ViewResolvers of this dispatcher.
	 * Can be overridden for custom resolution strategies, potentially based on
	 * specific model attributes or request parameters.
	 * @param viewName the name of the view to resolve
	 * @param model the model to be passed to the view
	 * @param locale the current locale
	 * @param request current HTTP servlet request
	 * @return the View object, or {@code null} if none found
	 * @throws Exception if the view cannot be resolved
	 * (typically in case of problems creating an actual View object)
	 * @see ViewResolver#resolveViewName
	 *
	 * @tips 使用 viewName 获得 View 对象。
	 */
	@Nullable
	protected View resolveViewName(String viewName, @Nullable Map<String, Object> model,
			Locale locale, HttpServletRequest request) throws Exception {

		if (this.viewResolvers != null) {
			// 遍历 ViewResolver 数组
			for (ViewResolver viewResolver : this.viewResolvers) {
				// 以InternalResourceViewResolver 为例进行分析
				// 根据 viewName + locale 参数，解析出 View 对象
				View view = viewResolver.resolveViewName(viewName, locale);
				if (view != null) {
					// 解析成功，直接返回 View 对象
					return view;
				}
			}
		}
		// 返回空
		return null;
	}

	private void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerExecutionChain mappedHandler, Exception ex) throws Exception {

		if (mappedHandler != null) {
			mappedHandler.triggerAfterCompletion(request, response, ex);
		}
		throw ex;
	}

	/**
	 * Restore the request attributes after an include.
	 * @param request current HTTP request
	 * @param attributesSnapshot the snapshot of the request attributes before the include
	 */
	@SuppressWarnings("unchecked")
	private void restoreAttributesAfterInclude(HttpServletRequest request, Map<?, ?> attributesSnapshot) {
		// Need to copy into separate Collection here, to avoid side effects
		// on the Enumeration when removing attributes.
		Set<String> attrsToCheck = new HashSet<>();
		Enumeration<?> attrNames = request.getAttributeNames();
		while (attrNames.hasMoreElements()) {
			String attrName = (String) attrNames.nextElement();
			if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
				attrsToCheck.add(attrName);
			}
		}

		// Add attributes that may have been removed
		attrsToCheck.addAll((Set<String>) attributesSnapshot.keySet());

		// Iterate over the attributes to check, restoring the original value
		// or removing the attribute, respectively, if appropriate.
		for (String attrName : attrsToCheck) {
			Object attrValue = attributesSnapshot.get(attrName);
			if (attrValue == null) {
				request.removeAttribute(attrName);
			}
			else if (attrValue != request.getAttribute(attrName)) {
				request.setAttribute(attrName, attrValue);
			}
		}
	}

	private static String getRequestUri(HttpServletRequest request) {
		String uri = (String) request.getAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE);
		if (uri == null) {
			uri = request.getRequestURI();
		}
		return uri;
	}

}
