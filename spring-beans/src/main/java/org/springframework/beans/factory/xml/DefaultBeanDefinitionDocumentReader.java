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

package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of the {@link BeanDefinitionDocumentReader} interface that
 * reads bean definitions according to the "spring-beans" DTD and XSD format
 * (Spring's default XML bean definition format).
 *
 * <p>The structure, elements, and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). {@code <beans>} does not need to be the root
 * element of the XML document: this class will parse all bean definition elements
 * in the XML file, regardless of the actual root element.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Erik Wiersma
 * @since 18.12.2003
 */
public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {

	public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;

	public static final String NESTED_BEANS_ELEMENT = "beans";

	public static final String ALIAS_ELEMENT = "alias";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String ALIAS_ATTRIBUTE = "alias";

	public static final String IMPORT_ELEMENT = "import";

	public static final String RESOURCE_ATTRIBUTE = "resource";

	public static final String PROFILE_ATTRIBUTE = "profile";


	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private XmlReaderContext readerContext;

	@Nullable
	private BeanDefinitionParserDelegate delegate;


	/**
	 * This implementation parses bean definitions according to the "spring-beans" XSD
	 * (or DTD, historically).
	 * <p>Opens a DOM Document; then initializes the default settings
	 * specified at the {@code <beans/>} level; then parses the contained bean definitions.
	 *
	 * @tips 从给定的 Document 对象中解析定义的 BeanDefinition 并将他们注册到注册表中 。
	 * 方法接收两个参数，待解析的 Document 对象，以及解析器的当前上下文，包括目标注册表和被解析的资源。
	 * readerContext 是根据 Resource 来创建的
	 */
	@Override
	public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
		this.readerContext = readerContext;
		doRegisterBeanDefinitions(doc.getDocumentElement());
	}

	/**
	 * Return the descriptor for the XML resource that this parser works on.
	 */
	protected final XmlReaderContext getReaderContext() {
		Assert.state(this.readerContext != null, "No XmlReaderContext available");
		return this.readerContext;
	}

	/**
	 * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor}
	 * to pull the source metadata from the supplied {@link Element}.
	 */
	@Nullable
	protected Object extractSource(Element ele) {
		return getReaderContext().extractSource(ele);
	}


	/**
	 * Register each bean definition within the given root {@code <beans/>} element.
	 *
	 * @tips 程序首先处理 profile属性，profile主要用于我们切换环境，比如切换开发、测试、生产环境，非常方便。
	 * 然后调用 parseBeanDefinitions() 进行解析动作，不过在该方法之前之后分别调用 preProcessXml() 和 postProcessXml() 方法来进行前、后处理，
	 * 目前这两个方法都是空实现，交由子类来实现。
	 */
	@SuppressWarnings("deprecation")  // for Environment.acceptsProfiles(String...)
	protected void doRegisterBeanDefinitions(Element root) {
		// Any nested <beans> elements will cause recursion in this method. In
		// order to propagate and preserve <beans> default-* attributes correctly,
		// keep track of the current (parent) delegate, which may be null. Create
		// the new (child) delegate with a reference to the parent for fallback purposes,
		// then ultimately reset this.delegate back to its original (parent) reference.
		// this behavior emulates a stack of delegates without actually necessitating one.
		// 记录老的 BeanDefinitionParserDelegate 对象
		BeanDefinitionParserDelegate parent = this.delegate;
		// <1> 创建 BeanDefinitionParserDelegate 对象，并进行设置到 delegate
		this.delegate = createDelegate(getReaderContext(), root, parent);
		// <2> 检查 <beans /> 根标签的命名空间是否为空，或者是 http://www.springframework.org/schema/beans
		if (this.delegate.isDefaultNamespace(root)) {
			// 处理 profile
			// <2.1> 处理 profile 属性。可参见《Spring3自定义环境配置 <beans profile="">》http://nassir.iteye.com/blog/1535799
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
			if (StringUtils.hasText(profileSpec)) {
				// <2.2> 使用分隔符切分，可能有多个 profile 。
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				// We cannot use Profiles.of(...) since profile expressions are not supported
				// in XML config. See SPR-12458 for details.
				// <2.3> 如果所有 profile 都无效，则不进行注册
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Skipped XML bean definition file due to specified profiles [" + profileSpec +
								"] not matching: " + getReaderContext().getResource());
					}
					return;
				}
			}
		}

		// 解析前处理
		preProcessXml(root);
		// 解析
		parseBeanDefinitions(root, this.delegate);
		// 解析后处理
		postProcessXml(root);

		this.delegate = parent;
	}

	protected BeanDefinitionParserDelegate createDelegate(
			XmlReaderContext readerContext, Element root, @Nullable BeanDefinitionParserDelegate parentDelegate) {

		BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
		delegate.initDefaults(root, parentDelegate);
		return delegate;
	}

	/**
	 * Parse the elements at the root level in the document:
	 * "import", "alias", "bean".
	 * @param root the DOM root element of the document
	 *
	 * @tips 最终解析动作落地在两个方法处：parseDefaultElement(ele, delegate) 和 delegate.parseCustomElement(root)。
	 * 我们知道在 Spring 有两种 Bean 声明方式：
	 *
	 * 配置文件式声明：<bean id="studentService" class="org.springframework.core.StudentService"/>
	 * 自定义注解方式：<tx:annotation-driven>
	 *
	 * 两种方式的读取和解析都存在较大的差异，所以采用不同的解析方法，如果根节点或者子节点采用默认命名空间的话，则调用 parseDefaultElement() 进行解析，
	 * 否则调用 delegate.parseCustomElement() 方法进行自定义解析。
	 *
	 * @tips 解析 BeanDefinition 的入口在 DefaultBeanDefinitionDocumentReader.parseBeanDefinitions() 。
	 * 该方法会根据命令空间来判断标签是默认标签还是自定义标签，其中默认标签由 parseDefaultElement() 实现，自定义标签由 parseCustomElement() 实现。
	 * 在默认标签解析中，会根据标签名称的不同进行 import 、alias 、bean 、beans 四大标签进行处理，其中 bean 标签的解析为核心，
	 * 它由 processBeanDefinition() 方法实现。processBeanDefinition() 开始进入解析核心工作，分为三步：
	 *
	 * 				解析默认标签：BeanDefinitionParserDelegate.parseBeanDefinitionElement()
	 * 				解析默认标签下的自定义标签：BeanDefinitionParserDelegate.decorateBeanDefinitionIfRequired()
	 * 				注册解析的 BeanDefinition：BeanDefinitionReaderUtils.registerBeanDefinition
	 *
	 * 在默认标签解析过程中，核心工作由 parseBeanDefinitionElement() 方法实现，该方法会依次解析 Bean 标签的属性、各个子元素，
	 * 解析完成后返回一个 GenericBeanDefinition 实例对象。
	 */
	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		// <1> 如果根节点使用默认命名空间，执行默认解析
		if (delegate.isDefaultNamespace(root)) {
			// 遍历子节点
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (node instanceof Element) {
					Element ele = (Element) node;
					// <1> 如果该节点使用默认命名空间，执行默认解析
					if (delegate.isDefaultNamespace(ele)) {
						parseDefaultElement(ele, delegate);
					}
					// 如果该节点非默认命名空间，执行自定义解析
					else {
						delegate.parseCustomElement(ele);
					}
				}
			}
		}
		// <2> 如果根节点非默认命名空间，执行自定义解析
		else {
			/**
			 * 自定义标签的解析过程已经分析完成了。其实整个过程还是较为简单：首先会加载 handlers 文件，
			 * 将其中内容进行一个解析，形成 <namespaceUri,类路径> 这样的一个映射，然后根据获取的 namespaceUri 就可以得到相应的类路径，
			 * 对其进行初始化等到相应的 Handler 对象，调用 parse() 方法，
			 * 在该方法中根据标签的 localName 得到相应的 BeanDefinitionParser 实例对象，调用 parse() ，
			 * 该方法定义在 AbstractBeanDefinitionParser 抽象类中，核心逻辑封装在其 parseInternal() 中，
			 * 该方法返回一个 AbstractBeanDefinition 实例对象，其主要是在 AbstractSingleBeanDefinitionParser 中实现，
			 * 对于自定义的 Parser 类，其需要实现 getBeanClass() 或者 getBeanClassName() 和 doParse()。
			 */
			delegate.parseCustomElement(root);
		}
	}

	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		// 对 import 标签的解析
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			importBeanDefinitionResource(ele);
		}
		// 对 alias 标签的解析
		else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			processAliasRegistration(ele);
		}
		// 对 bean 标签的解析
		else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			processBeanDefinition(ele, delegate);
		}
		// 对 beans 标签的解析
		else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			// recurse 递归
			doRegisterBeanDefinitions(ele);
		}
	}

	/**
	 * Parse an "import" element and load the bean definitions
	 * from the given resource into the bean factory.
	 *
	 * @tips spring.xml 配置文件中使用 import 标签的方式导入其他模块的配置文件，如果有配置需要修改直接修改相应配置文件即可，
	 * 若有新的模块需要引入直接增加 import 即可，这样大大简化了配置后期维护的复杂度，同时也易于管理。
	 *
	 * @tips  相对路径 如果是相对路径则会根据相应的 Resource 计算出相应的绝对路径，然后根据该路径构造一个 Resource，
	 * 若该 Resource 存在，则调用 XmlBeanDefinitionReader.loadBeanDefinitions() 进行 BeanDefinition 加载，
	 * 否则构造一个绝对 location ，调用 AbstractBeanDefinitionReader.loadBeanDefinitions() 方法，与绝对路径过程一样。
	 * 至此，import 标签解析完毕，整个过程比较清晰明了：
	 * 获取 source 属性值，得到正确的资源路径，然后调用 loadBeanDefinitions() 方法进行递归的 BeanDefinition 加载
	 */
	protected void importBeanDefinitionResource(Element ele) {
		// 获取 resource 的属性值
		String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
		// 为空，直接退出
		if (!StringUtils.hasText(location)) {
			getReaderContext().error("Resource location must not be empty", ele);
			return;
		}

		// Resolve system properties: e.g. "${user.dir}"
		// 解析系统属性，格式如 ："${user.dir}"
		location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

		Set<Resource> actualResources = new LinkedHashSet<>(4);

		// Discover whether the location is an absolute or relative URI
		// 判断 location 是相对路径还是绝对路径
		boolean absoluteLocation = false;
		try {
			/**
			 * 判断绝对路径的规则如下：
			 *
			 * 以 classpath*: 或者 classpath: 开头为绝对路径
			 * 能够通过该 location 构建出 java.net.URL为绝对路径
			 *
			 * 根据 location 构造 java.net.URI 判断调用 isAbsolute() 判断是否为绝对路径
			 */
			absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
		}
		catch (URISyntaxException ex) {
			// cannot convert to an URI, considering the location relative
			// unless it is the well-known Spring prefix "classpath*:"
		}

		// Absolute or relative?
		// 绝对路径
		if (absoluteLocation) {
			try {
				// 直接根据地址加载相应的配置文件
				/**
				 * 绝对路径 如果 location 为绝对路径则调用 loadBeanDefinitions()，该方法在 AbstractBeanDefinitionReader 中定义。
				 */
				int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from URL location [" + location + "]");
				}
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from URL location [" + location + "]", ele, ex);
			}
		}
		else {
			// No URL -> considering resource location as relative to the current file.
			try {
				int importCount;
				// 相对路径则根据相应的地质计算出绝对路径地址
				Resource relativeResource = getReaderContext().getResource().createRelative(location);
				if (relativeResource.exists()) {
					importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
					actualResources.add(relativeResource);
				}
				else {
					String baseLocation = getReaderContext().getResource().getURL().toString();
					importCount = getReaderContext().getReader().loadBeanDefinitions(
							StringUtils.applyRelativePath(baseLocation, location), actualResources);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from relative location [" + location + "]");
				}
			}
			catch (IOException ex) {
				getReaderContext().error("Failed to resolve current resource location", ele, ex);
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from relative location [" + location + "]", ele, ex);
			}
		}
		// 解析成功后，进行监听器激活处理
		Resource[] actResArray = actualResources.toArray(new Resource[0]);
		getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
	}

	/**
	 * Process the given alias element, registering the alias with the registry.
	 */
	protected void processAliasRegistration(Element ele) {
		// 获取 beanName
		String name = ele.getAttribute(NAME_ATTRIBUTE);
		// 获取 alias
		String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
		boolean valid = true;
		if (!StringUtils.hasText(name)) {
			getReaderContext().error("Name must not be empty", ele);
			valid = false;
		}
		if (!StringUtils.hasText(alias)) {
			getReaderContext().error("Alias must not be empty", ele);
			valid = false;
		}
		if (valid) {
			try {
				// 注册alias
				getReaderContext().getRegistry().registerAlias(name, alias);
			}
			catch (Exception ex) {
				getReaderContext().error("Failed to register alias '" + alias +
						"' for bean with name '" + name + "'", ele, ex);
			}
			getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
		}
	}

	/**
	 * Process the given bean element, parsing the bean definition
	 * and registering it with the registry.
	 *
	 * @tips 整个过程分为四个步骤
	 *
	 * 调用 BeanDefinitionParserDelegate.parseBeanDefinitionElement() 进行元素解析，解析过程中如果失败，返回 null，错误由 ProblemReporter 处理。
	 * 如果解析成功则返回 BeanDefinitionHolder 实例 bdHolder。BeanDefinitionHolder 为持有 name 和 alias 的 BeanDefinition。
	 *
	 * 若实例 bdHolder 不为空，则调用 BeanDefinitionParserDelegate.decorateBeanDefinitionIfRequired() 进行自定义标签处理
	 * 若存在默认标签的子节点下再有自定义属性需要再次对自定义标签进行解析
	 *
	 * 解析完成后，则调用 BeanDefinitionReaderUtils.registerBeanDefinition() 对 bdHolder 进行注册
	 *
	 * 发出响应事件，通知相关的监听器，完成 Bean 标签解析
	 */
	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			/**
			 * 对于配置文件，解析已经完成，装饰也已经完成，可以注册了
			 */
			try {
				// Register the final decorated instance.
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// Send registration event.
			/**
			 * ，这里的实现只为扩展，当程序开发人员需要对注册 BeanDefinition 事件进行监听
			 * 时可以通过注册监听器的方式并将处理逻辑写入监听器巾，目前在 Spring 中并没有对此事件做
			 * 任何逻辑处理
			 */
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}


	/**
	 * Allow the XML to be extensible by processing any custom element types first,
	 * before we start to process the bean definitions. This method is a natural
	 * extension point for any other custom pre-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void preProcessXml(Element root) {
	}

	/**
	 * Allow the XML to be extensible by processing any custom element types last,
	 * after we finished processing the bean definitions. This method is a natural
	 * extension point for any other custom post-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void postProcessXml(Element root) {
	}

}
