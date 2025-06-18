// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.aspect;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.ParameterizedType;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hiero.mirror.common.filter.ApiTrackingFilter;
import org.hiero.mirror.common.util.TestExecutionTracker;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

@Aspect
@AllArgsConstructor
public class RepositoryUsageTrackerAspect {

    private static final String CRUD_REPOSITORY = "CrudRepository";
    private static final String UNKNOWN_TABLE = "UNKNOWN_TABLE";

    private final EntityManager entityManager;

    @Getter
    private static final Map<String, Map<String, Set<String>>> apiTableQueries = new ConcurrentHashMap<>();

    @Around("execution(* org.hiero.mirror..repository.*.*(..))")
    public Object trackRepositoryCall(final ProceedingJoinPoint joinPoint) throws Throwable {
        if (!TestExecutionTracker.isTestRunning()) { // guard rail: only run during test execution
            return joinPoint.proceed();
        }

        final var endpoint =
                Optional.ofNullable(ApiTrackingFilter.getCurrentEndpoint()).orElse("UNKNOWN_ENDPOINT");

        final var repository = joinPoint.getTarget();
        final var tableName = resolveEntityTableName(repository);

        var method = joinPoint.getSignature().toShortString();

        if (method.contains(CRUD_REPOSITORY)) {
            // If the invoked method belongs to CrudRepository (e.g., findById, deleteAll),
            // it will appear as CrudRepository.methodName in the report.
            // To improve traceability, CrudRepository is replaced with the actual repository class name
            // that invoked the method.
            method = method.replace(CRUD_REPOSITORY, getRepositoryName(repository));
        }

        apiTableQueries
                .computeIfAbsent(endpoint, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(tableName, k -> new LinkedHashSet<>())
                .add(method);

        return joinPoint.proceed();
    }

    private String resolveEntityTableName(final Object repository) {
        final var entityClass = extractEntityClassFromRepository(repository);
        if (entityClass != null) {
            return resolveJpaTableName(entityClass);
        }
        return UNKNOWN_TABLE;
    }

    private Class<?> extractEntityClassFromRepository(final Object repositoryClass) {
        for (final var iface : repositoryClass.getClass().getGenericInterfaces()) {
            if (iface instanceof Class<?> ifaceClass) {
                for (final var superIface : ifaceClass.getGenericInterfaces()) {
                    if (superIface instanceof ParameterizedType paramType) {
                        final var rawType = paramType.getRawType();
                        if (rawType instanceof Class<?> rawClass && Repository.class.isAssignableFrom(rawClass)) {

                            final var entityType = paramType.getActualTypeArguments()[0];
                            if (entityType instanceof Class<?> entityClass) {
                                return entityClass;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private String convertCamelCaseToSnakeRegex(final String input) {
        return input.replaceAll("([A-Z])(?=[A-Z])", "$1_")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase();
    }

    private String resolveJpaTableName(final Class<?> entityClass) {
        final var name = entityManager.getMetamodel().getEntities().stream()
                .filter(e -> e.getJavaType().equals(entityClass))
                .map(EntityType::getName)
                .findFirst()
                .orElse(UNKNOWN_TABLE);

        return convertCamelCaseToSnakeRegex(name);
    }

    private String getRepositoryName(final Object repositoryInstance) {
        final var genericInterfaces = repositoryInstance.getClass().getGenericInterfaces();

        for (final var genericInterface : genericInterfaces) {
            if (genericInterface instanceof Class<?> repoInterface) {

                if (CrudRepository.class.isAssignableFrom(repoInterface)) {
                    return repoInterface.getSimpleName();
                }
            }
        }

        return "UNKNOWN_REPOSITORY"; // Default fallback
    }
}
