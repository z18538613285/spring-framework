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

package org.springframework.web.context;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Bootstrap listener to start up and shut down Spring's root {@link WebApplicationContext}.
 * Simply delegates to {@link ContextLoader} as well as to {@link ContextCleanupListener}.
 *
 * <p>As of Spring 3.1, {@code ContextLoaderListener} supports injecting the root web
 * application context via the {@link #ContextLoaderListener(WebApplicationContext)}
 * constructor, allowing for programmatic configuration in Servlet 3.0+ environments.
 * See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 17.02.2003
 * @see #setContextInitializers
 * @see org.springframework.web.WebApplicationInitializer
 *
 * @tips ContextLoaderListener 的作用就是启动 Web 容器时，自动装配 ApplicationContext的配置信息。
 * 因为它实现了 ServletContextListener 这个接口，在 web.xml 配置这个监听器，启动容器时，就会默认执行它
 * 实现的方法，使用 ServletContextListener 接口，开发者能够在为客户端请求提供服务前向 ServletContext 中
 * 添加任意的对象。这个对象在 ServletContext启动的时候被初始化，然后在 ServletContext 整个运行期间都是可见的。
 *
 * 每一个 Web 应用都有一个 ServletContext与之相关联。ServletContext对象在应用启动时被创建，在应用关闭的时候被销毁。
 * servletContext 在全局范围内有效，类似于应用中的一个全局变量。
 * 在 ServletContextListener 中的核心逻辑便是初始化 WebApplicationContext 实例并存放到 ServletContext 中。
 */
public class ContextLoaderListener extends ContextLoader implements ServletContextListener {

	/**
	 * Create a new {@code ContextLoaderListener} that will create a web application
	 * context based on the "contextClass" and "contextConfigLocation" servlet
	 * context-params. See {@link ContextLoader} superclass documentation for details on
	 * default values for each.
	 * <p>This constructor is typically used when declaring {@code ContextLoaderListener}
	 * as a {@code <listener>} within {@code web.xml}, where a no-arg constructor is
	 * required.
	 * <p>The created application context will be registered into the ServletContext under
	 * the attribute name {@link WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE}
	 * and the Spring application context will be closed when the {@link #contextDestroyed}
	 * lifecycle method is invoked on this listener.
	 * @see ContextLoader
	 * @see #ContextLoaderListener(WebApplicationContext)
	 * @see #contextInitialized(ServletContextEvent)
	 * @see #contextDestroyed(ServletContextEvent)
	 */
	public ContextLoaderListener() {
	}

	/**
	 * Create a new {@code ContextLoaderListener} with the given application context. This
	 * constructor is useful in Servlet 3.0+ environments where instance-based
	 * registration of listeners is possible through the {@link javax.servlet.ServletContext#addListener}
	 * API.
	 * <p>The context may or may not yet be {@linkplain
	 * org.springframework.context.ConfigurableApplicationContext#refresh() refreshed}. If it
	 * (a) is an implementation of {@link ConfigurableWebApplicationContext} and
	 * (b) has <strong>not</strong> already been refreshed (the recommended approach),
	 * then the following will occur:
	 * <ul>
	 * <li>If the given context has not already been assigned an {@linkplain
	 * org.springframework.context.ConfigurableApplicationContext#setId id}, one will be assigned to it</li>
	 * <li>{@code ServletContext} and {@code ServletConfig} objects will be delegated to
	 * the application context</li>
	 * <li>{@link #customizeContext} will be called</li>
	 * <li>Any {@link org.springframework.context.ApplicationContextInitializer ApplicationContextInitializer org.springframework.context.ApplicationContextInitializer ApplicationContextInitializers}
	 * specified through the "contextInitializerClasses" init-param will be applied.</li>
	 * <li>{@link org.springframework.context.ConfigurableApplicationContext#refresh refresh()} will be called</li>
	 * </ul>
	 * If the context has already been refreshed or does not implement
	 * {@code ConfigurableWebApplicationContext}, none of the above will occur under the
	 * assumption that the user has performed these actions (or not) per his or her
	 * specific needs.
	 * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
	 * <p>In any case, the given application context will be registered into the
	 * ServletContext under the attribute name {@link
	 * WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE} and the Spring
	 * application context will be closed when the {@link #contextDestroyed} lifecycle
	 * method is invoked on this listener.
	 * @param context the application context to manage
	 * @see #contextInitialized(ServletContextEvent)
	 * @see #contextDestroyed(ServletContextEvent)
	 *
	 * @tips 这两个构造方法，是因为父类 ContextLoader 有这两个构造方法，所以必须重新定义。
	 * 比较需要注意的是，第二个构造方法，可以直接传递一个 WebApplicationContext 对象，
	 * 那样，实际 ContextLoaderListener 就无需在创建一个新的 WebApplicationContext 对象
	 */
	public ContextLoaderListener(WebApplicationContext context) {
		super(context);
	}


	/**
	 * Initialize the root web application context.
	 */
	@Override
	public void contextInitialized(ServletContextEvent event) {
		// 初始化 WebApplicationContext
		/**
		 * 这里涉及了一个常用类 WebApplicationContext：在 Web 应用中，我们会用到 WebApplicationContext，
		 * 其继承于 ApplicationContext，在 ApplicationContext 的基础上又追加了一些特定于Web的操作及属性，
		 * 非常类似于通过编程方式使用Spring时使用的 ClassPathXmlApplicationContext 类提供的功能
		 *
		 * 调用父类 ContextLoader 的 方法，初始化 WebApplicationContext 对象
		 */
		initWebApplicationContext(event.getServletContext());
	}


	/**
	 * Close the root web application context.
	 *
	 * @tips 销毁 WebApplicationContext 容器的逻辑
	 */
	@Override
	public void contextDestroyed(ServletContextEvent event) {
		closeWebApplicationContext(event.getServletContext());
		ContextCleanupListener.cleanupAttributes(event.getServletContext());
	}

}
