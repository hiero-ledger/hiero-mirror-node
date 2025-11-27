// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RepositoryLoggingAspect {

    /**
     * Define a Pointcut that targets all public methods within classes that
     * implement the Spring Data Repository interface (e.g., JpaRepository, CrudRepository).
     * The '+' ensures it catches all subtypes of the Repository interface.
     */
    @Pointcut("execution(public * org.springframework.data.repository.Repository+.*(..))")
    public void repositoryMethodExecution() {
        // Pointcut definition method - body is intentionally empty
    }

    /**
     * Advice that wraps the repository method call.
     * This is executed BEFORE and AFTER the target method.
     */
    @Around("repositoryMethodExecution()")
    public Object logRepositoryCall(ProceedingJoinPoint joinPoint) throws Throwable {

        // --- 1. PRE-EXECUTION: Log Method and Parameters ---
        String threadName = Thread.currentThread().getName();
        // Get the fully qualified name of the Repository interface
        String repositoryName = joinPoint.getSignature().getDeclaringType().getSimpleName();

        // Get the method name (e.g., "findById", "findAllByEmail")
        String methodName = joinPoint.getSignature().getName();

        // Get the arguments passed to the method
        Object[] rawArgs = joinPoint.getArgs();
        String transformedArgs = Arrays.stream(rawArgs)
                .map(this::transformArg)
                .map(String::valueOf) // Convert each element to string
                .collect(Collectors.joining(", ", "[", "]"));

        long startTime = System.currentTimeMillis();

        // --- PROCEED with the original database operation ---
        Object result = joinPoint.proceed();

        // POST-EXECUTION (Success)
        long duration = System.currentTimeMillis() - startTime;
        System.out.println(threadName + ": " + repositoryName + "." + methodName + "(" + transformedArgs + ") in "
                + duration + "ms");
        return result;
    }

    private Object transformArg(Object arg) {
        if (arg == null) {
            return "null";
        }

        // Check if the argument is a byte array
        if (arg.getClass().isArray() && arg.getClass().getComponentType().equals(byte.class)) {
            // Use HexFormat for clean, concise hex output
            return "0x" + HexFormat.of().formatHex((byte[]) arg);
        }

        // Check for common byte array types in collections (less common, but safe)
        if (arg instanceof byte[]) {
            return "0x" + HexFormat.of().formatHex((byte[]) arg);
        }

        return arg;
    }
}
