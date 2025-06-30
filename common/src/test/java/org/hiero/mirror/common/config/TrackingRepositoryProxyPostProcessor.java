// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.hiero.mirror.common.interceptor.RepositoryUsageInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;

@AllArgsConstructor
public final class TrackingRepositoryProxyPostProcessor implements RepositoryProxyPostProcessor {

    private final EntityManager entityManager;

    @Override
    public void postProcess(final ProxyFactory factory, final RepositoryInformation repositoryInformation) {
        factory.addAdvice(new RepositoryUsageInterceptor(repositoryInformation, entityManager));
    }
}
