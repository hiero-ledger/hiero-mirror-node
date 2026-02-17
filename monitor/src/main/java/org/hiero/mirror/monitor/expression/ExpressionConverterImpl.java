// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.expression;

import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.Strings;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.monitor.exception.ExpressionConversionException;
import org.hiero.mirror.monitor.publish.PublishRequest;
import org.hiero.mirror.monitor.publish.PublishResponse;
import org.hiero.mirror.monitor.publish.PublishScenario;
import org.hiero.mirror.monitor.publish.PublishScenarioProperties;
import org.hiero.mirror.monitor.publish.TransactionPublisher;
import org.hiero.mirror.monitor.publish.transaction.AdminKeyable;
import org.hiero.mirror.monitor.publish.transaction.TransactionType;
import org.hiero.mirror.monitor.publish.transaction.schedule.ScheduleCreateTransactionSupplier;
import org.hiero.mirror.monitor.publish.transaction.token.TokenCreateTransactionSupplier;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@CustomLog
@Named
@RequiredArgsConstructor
public class ExpressionConverterImpl implements ExpressionConverter {

    private static final String EXPRESSION_START = "${";
    private static final String EXPRESSION_END = "}";
    private static final Pattern EXPRESSION_PATTERN =
            Pattern.compile("\\$\\{(account|nft|token|topic|schedule)\\.(\\w+)}");

    private final Map<Expression, String> expressions = new ConcurrentHashMap<>();
    private final MonitorProperties monitorProperties;
    private final TransactionPublisher transactionPublisher;

    @Override
    public String convert(String property) {
        if (!Strings.CS.startsWith(property, EXPRESSION_START) || !Strings.CS.endsWith(property, EXPRESSION_END)) {
            return property;
        }

        Expression expression = parse(property);
        String convertedProperty = expressions.computeIfAbsent(expression, this::doConvert);
        log.info("Converted property {} to {}", property, convertedProperty);
        return convertedProperty;
    }

    private synchronized String doConvert(Expression expression) {
        if (expressions.containsKey(expression)) {
            return expressions.get(expression);
        }

        try {
            log.debug("Processing expression {}", expression);
            ExpressionType type = expression.getType();
            final var transactionSupplier =
                    type.getTransactionType().getSupplier().get();

            if (transactionSupplier instanceof AdminKeyable adminKeyable) {
                PrivateKey privateKey =
                        PrivateKey.fromString(monitorProperties.getOperator().getPrivateKey());
                adminKeyable.setAdminKey(privateKey.getPublicKey().toString());
            }

            if (transactionSupplier instanceof TokenCreateTransactionSupplier tokenSupplier) {
                tokenSupplier.setTreasuryAccountId(
                        monitorProperties.getOperator().getAccountId());
                if (type == ExpressionType.NFT) {
                    tokenSupplier.setType(TokenType.NON_FUNGIBLE_UNIQUE);
                }
            }

            // if ScheduleCreate set the properties to the inner scheduledTransactionProperties
            if (transactionSupplier instanceof ScheduleCreateTransactionSupplier scheduleCreateTransactionSupplier) {
                scheduleCreateTransactionSupplier.setOperatorAccountId(
                        monitorProperties.getOperator().getAccountId());
                scheduleCreateTransactionSupplier.setPayerAccount(
                        monitorProperties.getOperator().getAccountId());
            }

            PublishScenarioProperties publishScenarioProperties = new PublishScenarioProperties();
            publishScenarioProperties.setName(expression.toString());
            publishScenarioProperties.setTimeout(Duration.ofSeconds(30L));
            publishScenarioProperties.setType(type.getTransactionType());
            PublishScenario scenario = new PublishScenario(publishScenarioProperties);

            // We use explicit retry instead of the SDK retry since we need to regenerate the transaction to
            // avoid transaction expired errors
            Retry retrySpec = Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1L))
                    .maxBackoff(Duration.ofSeconds(8L))
                    .scheduler(Schedulers.newSingle("expression"))
                    .doBeforeRetry(r -> log.warn(
                            "Retry attempt #{} after failure: {}",
                            r.totalRetries() + 1,
                            r.failure().getMessage()));

            return Mono.defer(() -> transactionPublisher.publish(PublishRequest.builder()
                            .receipt(true)
                            .scenario(scenario)
                            .timestamp(Instant.now())
                            .transaction(
                                    transactionSupplier.get().setMaxAttempts(1).setTransactionMemo(scenario.getMemo()))
                            .build()))
                    .retryWhen(retrySpec)
                    .map(PublishResponse::getReceipt)
                    .map(type.getIdExtractor()::apply)
                    .doOnSuccess(id -> log.info("Created {} entity {}", type, id))
                    .doOnError(e -> log.error("Error converting expression: {}", e))
                    .toFuture()
                    .join();
        } catch (Exception e) {
            log.error("Error converting expression {}:", expression, e);
            throw new ExpressionConversionException(e);
        }
    }

    private Expression parse(String expression) {
        Matcher matcher = EXPRESSION_PATTERN.matcher(expression);

        if (!matcher.matches() || matcher.groupCount() != 2) {
            throw new IllegalArgumentException("Not a valid property expression: " + expression);
        }

        ExpressionType type = ExpressionType.valueOf(matcher.group(1).toUpperCase());
        String id = matcher.group(2);
        return new Expression(type, id);
    }

    @Getter
    @RequiredArgsConstructor
    private enum ExpressionType {
        ACCOUNT(TransactionType.ACCOUNT_CREATE, r -> r.accountId.toString()),
        NFT(TransactionType.TOKEN_CREATE, r -> r.tokenId.toString()),
        SCHEDULE(TransactionType.SCHEDULE_CREATE, r -> r.scheduleId.toString()),
        TOKEN(TransactionType.TOKEN_CREATE, r -> r.tokenId.toString()),
        TOPIC(TransactionType.CONSENSUS_CREATE_TOPIC, r -> r.topicId.toString());

        private final TransactionType transactionType;
        private final Function<TransactionReceipt, String> idExtractor;
    }

    @Value
    private class Expression {
        private ExpressionType type;
        private String id;

        @Override
        public String toString() {
            return type.name().toLowerCase() + "." + id;
        }
    }
}
