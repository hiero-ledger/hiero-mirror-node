// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.interceptor;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.query.spi.QueryImplementor;
import org.hiero.mirror.common.filter.ApiTrackingFilter;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.SqlParsingUtil;
import org.hiero.mirror.common.util.TestExecutionTracker;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.core.RepositoryInformation;

import static org.hiero.mirror.common.util.Constants.UNKNOWN_ENDPOINT;

@CustomLog
@RequiredArgsConstructor
public class RepositoryUsageInterceptor implements MethodInterceptor {
    private static final Map<String, Map<String, Set<String>>> API_TABLE_QUERIES = new ConcurrentHashMap<>();

    public static Map<String, Map<String, Set<String>>> getApiTableQueries() {
        return API_TABLE_QUERIES;
    }

    private final RepositoryInformation repositoryInformation;
    private final EntityManager entityManager;

    /**
     * Intercepts repository method invocation to track accessed database tables per API endpoint.
     * Only tracks during test execution as determined by {@link TestExecutionTracker}.
     * Extracts SQL queries either from native {@code @Query} annotations or Hibernate's query string,
     * then parses table names from the SQL.
     * Falls back to resolving the table name from the JPA entity metadata if no SQL found.
     *
     * @param invocation the method invocation context
     * @return the method invocation's original return value
     * @throws Throwable if the underlying method invocation throws
     */
    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        // Only track during test execution
        if (!TestExecutionTracker.isTestRunning()) {
            return invocation.proceed();
        }

        final var endpoint =
                Optional.ofNullable(ApiTrackingFilter.getCurrentEndpoint()).orElse(UNKNOWN_ENDPOINT);

        // Attempt to extract SQL (via @Query or Hibernate)
        final var sql = extractSql(invocation);
        var tableNames = (sql != null) ? SqlParsingUtil.extractTableNamesFromSql(sql) : Set.<String>of();

        // Fallback to entity class if no table names found
        if (tableNames.isEmpty()) {
            final var entityClass = repositoryInformation.getDomainType();
            final var tableName = resolveJpaTableName(entityClass);
            tableNames = Set.of(tableName);
        }

        final var methodSignature = createMethodSignature(invocation);

        // Track method under each resolved table for the current endpoint
        final var endpointMap = API_TABLE_QUERIES.computeIfAbsent(endpoint, k -> new ConcurrentHashMap<>());
        for (final var tableName : tableNames) {
            endpointMap
                    .computeIfAbsent(tableName, k -> ConcurrentHashMap.newKeySet())
                    .add(methodSignature);
        }

        return invocation.proceed();
    }

    /**
     * Resolves the JPA table name for the given entity class by querying the JPA metamodel.
     * Converts the entity name to snake_case.
     *
     * @param entityClass the JPA entity class
     * @return the resolved table name, or {@code "UNKNOWN_TABLE"} if not found
     */
    private String resolveJpaTableName(final Class<?> entityClass) {
        return entityManager.getMetamodel().getEntities().stream()
                .filter(e -> e.getJavaType().equals(entityClass))
                .map(EntityType::getName)
                .map(DomainUtils::toSnakeCase)
                .findFirst()
                .orElse("UNKNOWN_TABLE");
    }

    /**
     * Extracts the native SQL query string from the {@link Query} annotation on the given method, if present.
     *
     * @param method the repository method
     * @return the native SQL query string if present and marked native, otherwise {@code null}
     */
    private String extractSqlFromQueryAnnotation(final Method method) {
        final var queryAnnotation = method.getAnnotation(Query.class);
        if (queryAnnotation != null && queryAnnotation.nativeQuery()) {
            return queryAnnotation.value();
        }
        return null;
    }

    /**
     * Attempts to extract the SQL query string executed by the repository method invocation.
     * First tries to get native SQL from {@link Query} annotation,
     * then attempts to invoke the method and check if it returns a Hibernate {@link QueryImplementor}
     * to obtain the query string.
     *
     * @param invocation the method invocation context
     * @return the SQL query string if available, or {@code null} if not extractable
     */
    private String extractSql(final MethodInvocation invocation) {
        try {
            // native @Query sql
            final var nativeSql = extractSqlFromQueryAnnotation(invocation.getMethod());
            if (nativeSql != null) {
                return nativeSql;
            }

            final var result = invocation.getMethod().invoke(invocation.getThis(), invocation.getArguments());
            if (result instanceof QueryImplementor<?> queryImpl) {
                return queryImpl.getQueryString();
            }

        } catch (Exception ignored) {
            // Fail silently on any error during SQL extraction
        }
        return null;
    }

    /**
     * Constructs a method signature string for the invoked repository method in the form:
     * {@code RepositoryInterface.methodName(ParameterType1, ParameterType2, ...)}.
     *
     * @param invocation the method invocation context
     * @return the formatted method signature string
     */
    private String createMethodSignature(final MethodInvocation invocation) {
        final var method = invocation.getMethod();
        final var repositoryName =
                repositoryInformation.getRepositoryInterface().getSimpleName();
        final var methodName = method.getName();
        final var params = method.getParameterTypes();
        final var sb =
                new StringBuilder(repositoryName).append(".").append(methodName).append("(");

        for (int i = 0; i < params.length; i++) {
            sb.append(params[i].getSimpleName());
            if (i < params.length - 1) {
                sb.append(", ");
            }
        }

        sb.append(")");
        return sb.toString();
    }
}
