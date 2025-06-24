// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.aspect;

import com.google.common.base.CaseFormat;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hiero.mirror.common.filter.ApiTrackingFilter;
import org.hiero.mirror.common.util.TestExecutionTracker;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

@Aspect
@AllArgsConstructor
public class RepositoryUsageTrackerAspect {

    private static final String CRUD_REPOSITORY = "CrudRepository";
    private static final String UNKNOWN_TABLE = "UNKNOWN_TABLE";
    private static final Pattern CTE_PATTERN = Pattern.compile("(\\w+)\\s+as\\s*\\(");
    private static final Pattern FROM_JOIN_PATTERN = Pattern.compile("\\bfrom\\s+([\\w\\.]+)|\\bjoin\\s+([\\w\\.]+)");
    private static final Pattern INSERT_PATTERN =
            Pattern.compile("\\binsert\\s+into\\s+([\\w\\.]+)", Pattern.CASE_INSENSITIVE);
    private static final List<Pattern> TABLE_PATTERNS = List.of(FROM_JOIN_PATTERN, INSERT_PATTERN);

    @Getter
    private static final Map<String, Map<String, Set<String>>> API_TABLE_QUERIES = new ConcurrentHashMap<>();

    private final EntityManager entityManager;

    /**
     * Aspect advice that intercepts all method executions in repository classes under
     * the org.hiero.mirror..repository package. Tracks accessed database tables for the
     * current API endpoint during test execution.
     *
     * @param joinPoint the join point representing the method invocation
     * @return the result of the intercepted method execution
     * @throws Throwable if the intercepted method throws any exceptions
     */
    @Around("execution(* org.hiero.mirror..repository.*.*(..))")
    public Object trackRepositoryCall(final ProceedingJoinPoint joinPoint) throws Throwable {
        // guard rail: only run during test execution
        if (!TestExecutionTracker.isTestRunning()) {
            return joinPoint.proceed();
        }

        final var endpoint =
                Optional.ofNullable(ApiTrackingFilter.getCurrentEndpoint()).orElse("UNKNOWN_ENDPOINT");

        final var repository = joinPoint.getTarget();
        final var repositoryClass = repository.getClass();
        final var methodName = joinPoint.getSignature().getName();
        final var methodArgs = joinPoint.getArgs();

        // Find the repository interface method that matches the join point method
        final var repositoryMethod = findRepositoryMethod(repositoryClass, methodName, methodArgs);

        var tableNames = new HashSet<String>();

        if (repositoryMethod != null && repositoryMethod.isAnnotationPresent(Query.class)) {
            final var query = repositoryMethod.getAnnotation(Query.class);
            if (query.nativeQuery()) {
                tableNames = (HashSet<String>) extractTableNamesFromSql(query.value());
            }
        }

        // Fall back to resolving table from entity if no native query or tables found
        if (tableNames.isEmpty()) {
            final var tableName = resolveEntityTableName(repository);
            tableNames.add(tableName);
        }

        var methodSignature = joinPoint.getSignature().toShortString();
        if (methodSignature.contains(CRUD_REPOSITORY)) {
            // If the invoked method belongs to CrudRepository (e.g., findById, deleteAll),
            // it will appear as CrudRepository.methodName in the report.
            // To improve traceability, CrudRepository is replaced with the actual repository class name
            // that invoked the method.
            methodSignature = methodSignature.replace(CRUD_REPOSITORY, getRepositoryName(repository));
        }

        var endpointMap = API_TABLE_QUERIES.computeIfAbsent(endpoint, k -> new ConcurrentHashMap<>());
        for (final var tableName : tableNames) {
            endpointMap
                    .computeIfAbsent(tableName, k -> ConcurrentHashMap.newKeySet())
                    .add(methodSignature);
        }

        return joinPoint.proceed();
    }

    private Method findRepositoryMethod(Class<?> repositoryClass, String methodName, Object[] args) {
        final var iface = findRepositoryInterface(repositoryClass);
        if (iface == null) {
            return null;
        }

        for (final var method : iface.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                return method;
            }
        }
        return null;
    }

    /**
     * Recursively searches for the first implemented interface of the given class
     * that is a subtype of {@link org.springframework.data.repository.Repository}.
     *
     * @param clazz the class to inspect for repository interfaces
     * @return the first matching repository interface, or null if none found
     */
    private Class<?> findRepositoryInterface(Class<?> clazz) {
        for (final var iface : clazz.getInterfaces()) {
            if (Repository.class.isAssignableFrom(iface)) {
                return iface;
            }
        }

        final var superclass = clazz.getSuperclass();
        if (superclass != null && !superclass.equals(Object.class)) {
            return findRepositoryInterface(superclass);
        }
        return null;
    }

    /**
     * Extracts table names from the provided native SQL query string using regex
     * matching for common SQL clauses such as FROM, JOIN, INSERT INTO, UPDATE, DELETE,
     * TRUNCATE, and DROP.
     * <p>
     * Note: This is a heuristic approach and may not cover all complex queries.
     *
     * @param sql the native SQL query string to analyze
     * @return a set of unique table names found in the query
     */
    private Set<String> extractTableNamesFromSql(String sql) {
        final var tables = new HashSet<String>();
        if (sql == null || sql.isBlank()) {
            return tables;
        }

        final var loweredSql = sql.toLowerCase();

        // extracting common table expressions
        final var cteNames = new HashSet<>();
        final var cteMatcher = CTE_PATTERN.matcher(loweredSql);
        while (cteMatcher.find()) {
            cteNames.add(cteMatcher.group(1));
        }

        for (final var pattern : TABLE_PATTERNS) {
            final var matcher = pattern.matcher(loweredSql);
            while (matcher.find()) {
                final var table = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (table != null && !cteNames.contains(table)) {
                    tables.add(table);
                }
            }
        }

        return tables;
    }

    /**
     * Resolves the database table name associated with the entity type managed by
     * the given repository instance. Falls back to a constant if the entity class
     * cannot be determined.
     *
     * @param repository the repository instance whose entity table name to resolve
     * @return the resolved table name, or a default unknown table constant if resolution fails
     */
    private String resolveEntityTableName(final Object repository) {
        final var entityClass = extractEntityClassFromRepository(repository);
        if (entityClass != null) {
            return resolveJpaTableName(entityClass);
        }
        return UNKNOWN_TABLE;
    }

    /**
     * Attempts to extract the entity class managed by a given repository instance by
     * inspecting the generic interfaces implemented by the repository.
     *
     * @param repositoryInstance the repository instance to inspect
     * @return the entity class managed by the repository, or null if not determinable
     */
    private Class<?> extractEntityClassFromRepository(final Object repositoryInstance) {
        final var interfaces = repositoryInstance.getClass().getGenericInterfaces();

        for (final var iface : interfaces) {
            final var entityClass = extractFromInterface(iface);
            if (entityClass != null) {
                return entityClass;
            }
        }

        return null;
    }

    private Class<?> extractFromInterface(final Type iface) {
        if (!(iface instanceof Class<?> ifaceClass)) {
            return null;
        }

        for (final var superIface : ifaceClass.getGenericInterfaces()) {
            if (!isParameterizedRepository(superIface)) {
                continue;
            }

            return getEntityClassFromParameterizedType((ParameterizedType) superIface);
        }

        return null;
    }

    private boolean isParameterizedRepository(final Type type) {
        if (!(type instanceof ParameterizedType paramType)) {
            return false;
        }
        final var rawType = paramType.getRawType();
        return rawType instanceof Class<?> rawClass && Repository.class.isAssignableFrom(rawClass);
    }

    private Class<?> getEntityClassFromParameterizedType(final ParameterizedType paramType) {
        final var entityType = paramType.getActualTypeArguments()[0];
        if (entityType instanceof Class<?> entityClass) {
            return entityClass;
        }
        return null;
    }

    private static String toSnakeCase(final String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, text);
    }

    /**
     * Resolves the JPA database table name for a given entity class using the JPA
     * metamodel. The table name is converted to snake_case.
     *
     * @param entityClass the entity class to resolve the table name for
     * @return the snake_case table name, or a default unknown table constant if not resolvable
     */
    private String resolveJpaTableName(final Class<?> entityClass) {
        final var name = entityManager.getMetamodel().getEntities().stream()
                .filter(e -> e.getJavaType().equals(entityClass))
                .map(EntityType::getName)
                .findFirst()
                .orElse(UNKNOWN_TABLE);

        return toSnakeCase(name);
    }

    /**
     * Attempts to determine the simple repository interface name implemented by the
     * given repository instance, primarily to improve reporting and traceability.
     *
     * @param repositoryInstance the repository instance to inspect
     * @return the simple name of the repository interface, or "UNKNOWN_REPOSITORY" if not found
     */
    private String getRepositoryName(final Object repositoryInstance) {
        final var genericInterfaces = repositoryInstance.getClass().getGenericInterfaces();

        for (final var genericInterface : genericInterfaces) {
            if (genericInterface instanceof Class<?> repoInterface
                    && CrudRepository.class.isAssignableFrom(repoInterface)) {
                return repoInterface.getSimpleName();
            }
        }

        // Default fallback
        return "UNKNOWN_REPOSITORY";
    }
}
