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

package org.springframework.remoting.support;

import java.lang.reflect.InvocationTargetException;

/**
 * Abstract base class for remote service exporters that are based
 * on deserialization of {@link RemoteInvocation} objects.
 *
 * <p>Provides a "remoteInvocationExecutor" property, with a
 * {@link DefaultRemoteInvocationExecutor} as default strategy.
 *
 * @author Juergen Hoeller
 * @since 1.1
 * @see RemoteInvocationExecutor
 * @see DefaultRemoteInvocationExecutor
 */
public abstract class RemoteInvocationBasedExporter extends RemoteExporter {

	private RemoteInvocationExecutor remoteInvocationExecutor = new DefaultRemoteInvocationExecutor();


	/**
	 * Set the RemoteInvocationExecutor to use for this exporter.
	 * Default is a DefaultRemoteInvocationExecutor.
	 * <p>A custom invocation executor can extract further context information
	 * from the invocation, for example user credentials.
	 */
	public void setRemoteInvocationExecutor(RemoteInvocationExecutor remoteInvocationExecutor) {
		this.remoteInvocationExecutor = remoteInvocationExecutor;
	}

	/**
	 * Return the RemoteInvocationExecutor used by this exporter.
	 */
	public RemoteInvocationExecutor getRemoteInvocationExecutor() {
		return this.remoteInvocationExecutor;
	}


	/**
	 * Apply the given remote invocation to the given target object.
	 * The default implementation delegates to the RemoteInvocationExecutor.
	 * <p>Can be overridden in subclasses for custom invocation behavior,
	 * possibly for applying additional invocation parameters from a
	 * custom RemoteInvocation subclass. Note that it is preferable to use
	 * a custom RemoteInvocationExecutor which is a reusable strategy.
	 * @param invocation the remote invocation
	 * @param targetObject the target object to apply the invocation to
	 * @return the invocation result
	 * @throws NoSuchMethodException if the method name could not be resolved
	 * @throws IllegalAccessException if the method could not be accessed
	 * @throws InvocationTargetException if the method invocation resulted in an exception
	 * @see RemoteInvocationExecutor#invoke
	 */
	protected Object invoke(RemoteInvocation invocation, Object targetObject)
			throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {

		if (logger.isTraceEnabled()) {
			logger.trace("Executing " + invocation);
		}
		try {
			return getRemoteInvocationExecutor().invoke(invocation, targetObject);
		}
		catch (NoSuchMethodException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Could not find target method for " + invocation, ex);
			}
			throw ex;
		}
		catch (IllegalAccessException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Could not access target method for " + invocation, ex);
			}
			throw ex;
		}
		catch (InvocationTargetException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Target method failed for " + invocation, ex.getTargetException());
			}
			throw ex;
		}
	}

	/**
	 * Apply the given remote invocation to the given target object, wrapping
	 * the invocation result in a serializable RemoteInvocationResult object.
	 * The default implementation creates a plain RemoteInvocationResult.
	 * <p>Can be overridden in subclasses for custom invocation behavior,
	 * for example to return additional context information. Note that this
	 * is not covered by the RemoteInvocationExecutor strategy!
	 * @param invocation the remote invocation
	 * @param targetObject the target object to apply the invocation to
	 * @return the invocation result
	 * @see #invoke
	 *
	 * @tips 根据反序列化方式得到的 RemoteInvocation 对象中的信息，进行方法调用。注意此时调用的实体并不是服务接口或者服务类，
	 * 而是之前在初始化时候构造的封装了服务接口以及服务类的代理。
	 *
	 * 这段函数有两点需要说明的地方
	 * 1、对应方法的激活也就是 invoke 方法的调用，虽然经过层层环绕，但是最终还是实现了一个我们熟知的调用 invocation.invoke(targetObject)，也就是
	 * 执行 RemoteInvocation 类中的 invoke 方法，大致的逻辑还是通过 RemoteInvocation 中对应的方法信息在 targetObject上去执行，此方法在分析 RMI
	 * 功能的时候已经分析过，不在赘述，但是在对于当前方法的targetObject 参数，此 targetObject 是代理类，调用代理类的时候需要考虑增强方法的调用，这里需要注意。
	 * 2、对于返回结果需要使用 RemoteInvocationResult 进行封装，因为无法保证所有的返回结果都继承 Serializable接口，继而进行序列化，
	 * 那么就需要使用 RemoteInvocationResult进行统一封装
	 */
	protected RemoteInvocationResult invokeAndCreateResult(RemoteInvocation invocation, Object targetObject) {
		try {
			// 激活代理类中对应的 invocation 中的方法
			Object value = invoke(invocation, targetObject);
			// 封装结果以便于序列化
			return new RemoteInvocationResult(value);
		}
		catch (Throwable ex) {
			return new RemoteInvocationResult(ex);
		}
	}

}
