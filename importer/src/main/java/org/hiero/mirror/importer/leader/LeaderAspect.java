// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.leader;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.CustomLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;

/**
 * This class uses Spring Cloud Kubernetes Leader to atomically elect a leader using Kubernetes primitives. This class
 * tracks those leader events and either allows or disallows the execution of a method annotated with @Leader based upon
 * whether this pod is currently leader or not.
 */
@Aspect
@CustomLog
@Order(1)
public class LeaderAspect implements LeaderService {

    private final AtomicBoolean leader = new AtomicBoolean(false);

    public LeaderAspect() {
        log.info("Starting as follower");
    }

    @Around("execution(@org.hiero.mirror.importer.leader.Leader * *(..)) && @annotation(leaderAnnotation)")
    public Object leader(ProceedingJoinPoint joinPoint, Leader leaderAnnotation) throws Throwable {
        String targetClass = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        log.trace("Verifying leadership status before invoking");

        if (!leader.get()) {
            log.debug("Not the leader. Skipping invocation of {}.{}()", targetClass, methodName);
            return null;
        }

        log.debug("Currently the leader, proceeding to invoke: {}.{}()", targetClass, methodName);
        return joinPoint.proceed();
    }

    @EventListener
    public void granted(OnGrantedEvent event) {
        if (leader.compareAndSet(false, true)) {
            log.info("Transitioned to leader: {}", event);
        }
    }

    @EventListener
    public void revoked(OnRevokedEvent event) {
        if (leader.compareAndSet(true, false)) {
            log.info("Transitioned to follower: {}", event);
        }
    }
}
