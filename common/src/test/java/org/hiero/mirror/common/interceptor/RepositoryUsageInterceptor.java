// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.interceptor;

import static org.hiero.mirror.common.tableusage.EndpointContext.UNKNOWN_ENDPOINT;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hiero.mirror.common.tableusage.EndpointContext;
import org.hiero.mirror.common.tableusage.SqlParsingUtil;
import org.hiero.mirror.common.tableusage.TestExecutionTracker;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.util.StringUtils;

@CustomLog
@RequiredArgsConstructor
public class RepositoryUsageInterceptor implements MethodInterceptor {

    private static final Map<String, Map<String, Set<String>>> API_TABLE_QUERIES = new ConcurrentHashMap<>();

    private final RepositoryInformation repositoryInformation;
    private final RelationalMappingContext mappingContext;

    public static Map<String, Map<String, Set<String>>> getApiTableQueries() {
        return API_TABLE_QUERIES;
    }

    /**
     * Intercepts repository method invocation to track accessed database tables per API endpoint. Only tracks during
     * test execution as determined by {@link TestExecutionTracker}. Extracts SQL from {@code @Query} on the method, then
     * parses table names from the SQL. Falls back to the Spring Data Relational table name for the domain type.
     */
    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        if (!TestExecutionTracker.isTestRunning()) {
            return invocation.proceed();
        }

        final var endpoint = EndpointContext.getCurrentEndpoint();

        if (endpoint == null || UNKNOWN_ENDPOINT.equals(endpoint)) {
            return invocation.proceed();
        }

        final var sql = extractSql(invocation);
        var tableNames = (sql != null) ? SqlParsingUtil.extractTableNamesFromSql(sql) : Set.<String>of();

        if (tableNames.isEmpty()) {
            final var entityClass = repositoryInformation.getDomainType();
            final var tableName = resolveRelationalTableName(entityClass);
            tableNames = Set.of(tableName);
        }

        final var methodSignature = createMethodSignature(invocation);

        final var endpointMap = API_TABLE_QUERIES.computeIfAbsent(endpoint, k -> new ConcurrentHashMap<>());
        for (final var tableName : tableNames) {
            endpointMap
                    .computeIfAbsent(tableName, k -> ConcurrentHashMap.newKeySet())
                    .add(methodSignature);
        }

        return invocation.proceed();
    }

    private String resolveRelationalTableName(final Class<?> entityClass) {
        var entity = mappingContext.getPersistentEntity(entityClass);
        if (entity == null) {
            return DomainUtils.toSnakeCase(entityClass.getSimpleName());
        }
        return entity.getTableName().getReference();
    }

    /**
     * Extracts the SQL query string from the Spring Data JDBC {@link Query} annotation on the given method, if present.
     */
    private String extractSqlFromQueryAnnotation(final Method method) {
        var queryAnnotation = method.getAnnotation(Query.class);
        if (queryAnnotation != null && StringUtils.hasText(queryAnnotation.value())) {
            return queryAnnotation.value();
        }
        return null;
    }

    /**
     * Attempts to extract SQL from the repository method (annotation only; derived query SQL is not available here).
     */
    private String extractSql(final MethodInvocation invocation) {
        return extractSqlFromQueryAnnotation(invocation.getMethod());
    }

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
