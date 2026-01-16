// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.domain.TransactionFilterFields;
import org.hiero.mirror.importer.exception.InvalidConfigurationException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hiero.mirror.importer.parser")
@Data
@Validated
public class CommonParserProperties {

    @Min(8192)
    private int bufferSize = 32768; // tested max byte size of buffer used by PGCopyOutputStream

    @NotNull
    @Valid
    private Collection<TransactionFilter> exclude = new ArrayList<>();

    @NotNull
    @Valid
    private Collection<TransactionFilter> include = new ArrayList<>();

    @Getter(lazy = true)
    private final Predicate<TransactionFilterFields> filter = includeFilter().and(excludeFilter());

    public boolean hasFilter() {
        return (!exclude.isEmpty()) || (!include.isEmpty());
    }

    private Predicate<TransactionFilterFields> excludeFilter() {
        if (exclude.isEmpty()) {
            return t -> true;
        }
        return exclude.stream().map(f -> f.getFilter().negate()).reduce(a -> true, Predicate::and);
    }

    private Predicate<TransactionFilterFields> includeFilter() {
        if (include.isEmpty()) {
            return t -> true;
        }
        return include.stream().map(TransactionFilter::getFilter).reduce(a -> false, Predicate::or);
    }

    @Data
    @Validated
    public static class TransactionFilter {

        private static final StandardEvaluationContext evaluationContext = new StandardEvaluationContext();

        static {
            evaluationContext.setTypeLocator(new RestrictedTypeLocator());
            evaluationContext.setPropertyAccessors(List.of(new RecordItemPropertyAccessor()));
        }

        private Collection<EntityId> entity = new LinkedHashSet<>();

        private String expression;

        @Getter(lazy = true)
        private final Expression parsedExpression = parseExpression();

        private Collection<TransactionType> transaction = new LinkedHashSet<>();

        private Expression parseExpression() {
            if (!StringUtils.isEmpty(expression)) {
                try {
                    var expressionParser = new SpelExpressionParser();
                    return expressionParser.parseExpression(expression);
                } catch (ParseException ex) {
                    throw new InvalidConfigurationException("Transaction filter expression failed to parse", ex);
                }
            }

            return null;
        }

        Predicate<TransactionFilterFields> getFilter() {
            return t -> matches(t) && matches(t.getEntities()) && matchesExpression(t.getRecordItem());
        }

        private boolean matches(TransactionFilterFields t) {
            if (transaction.isEmpty()) {
                return true;
            }

            return transaction.contains(TransactionType.of(t.getRecordItem().getTransactionType()));
        }

        private boolean matches(Collection<EntityId> entities) {
            if (entity.isEmpty()) {
                return true;
            }

            return entities != null && CollectionUtils.containsAny(entity, entities);
        }

        private boolean matchesExpression(RecordItem recordItem) {
            var expressionParsed = getParsedExpression();
            if (expressionParsed == null) {
                return true;
            }

            try {
                Boolean result = expressionParsed.getValue(evaluationContext, recordItem, Boolean.class);
                return Objects.requireNonNullElse(result, false);
            } catch (EvaluationException ex) {
                throw new InvalidConfigurationException(
                        "Transaction filter expression failed to evaluate: " + expression, ex);
            }
        }
    }

    /**
     * Limit scope of property access via SpEL for RecordItem instances.
     */
    static class RecordItemPropertyAccessor extends ReflectivePropertyAccessor {

        private static final Set<String> ACCESSIBLE_PROPERTIES = Set.of("transactionBody", "transactionRecord");

        RecordItemPropertyAccessor() {
            super(false);
        }

        @Override
        public boolean canRead(EvaluationContext context, Object target, String name) throws AccessException {
            if (target instanceof RecordItem && !ACCESSIBLE_PROPERTIES.contains(name)) {
                return false;
            }
            return super.canRead(context, target, name);
        }
    }

    /**
     * Limit the types that may be accessed via SpEL to none. Everything starts with property access.
     */
    static class RestrictedTypeLocator implements TypeLocator {

        @Override
        public Class<?> findType(String typeName) throws EvaluationException {
            throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
        }
    }
}
