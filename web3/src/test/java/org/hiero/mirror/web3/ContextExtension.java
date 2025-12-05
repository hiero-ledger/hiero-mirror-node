// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import java.lang.reflect.Method;
import lombok.CustomLog;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

/**
 * This JUnit extension initializes the ContractCallContext before each test and ensures it's cleaned up afterward.
 */
@CustomLog
public class ContextExtension implements InvocationInterceptor {

    @Override
    public <T> T interceptTestFactoryMethod(
            Invocation<T> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) {
        return intercept(invocation, invocationContext);
    }

    @Override
    public void interceptTestMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) {
        intercept(invocation, invocationContext);
    }

    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) {
        intercept(invocation, invocationContext);
    }

    private <T> T intercept(Invocation<T> invocation, ReflectiveInvocationContext<Method> invocationContext) {
        return ContractCallContext.run(context -> {
            try {
                return invocation.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }
}
