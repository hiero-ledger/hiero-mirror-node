// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.generator;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.hedera.mirror.monitor.expression.ExpressionConverter;
import com.hedera.mirror.monitor.properties.ScenarioPropertiesAggregator;
import com.hedera.mirror.monitor.publish.PublishRequest;
import com.hedera.mirror.monitor.publish.PublishScenario;
import com.hedera.mirror.monitor.publish.PublishScenarioProperties;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.account.AccountDeleteTransactionSupplier;
import com.hedera.mirror.monitor.validator.AccountIdValidator;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.CustomLog;
import lombok.Getter;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

@CustomLog
public class ConfigurableTransactionGenerator implements TransactionGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExpressionConverter expressionConverter;
    private final ScenarioPropertiesAggregator scenarioPropertiesAggregator;

    @Getter
    private final PublishScenarioProperties properties;

    private final AccountIdValidator accountIdValidator;
    private final Supplier<TransactionSupplier<?>> transactionSupplier;
    private final AtomicLong remaining;
    private final long stopTime;
    private final PublishRequest.PublishRequestBuilder builder;
    private final PublishScenario scenario;

    public ConfigurableTransactionGenerator(
            AccountIdValidator accountIdValidator,
            ExpressionConverter expressionConverter,
            ScenarioPropertiesAggregator scenarioPropertiesAggregator,
            PublishScenarioProperties properties) {
        this.accountIdValidator = accountIdValidator;
        this.expressionConverter = expressionConverter;
        this.scenarioPropertiesAggregator = scenarioPropertiesAggregator;
        this.properties = properties;
        transactionSupplier = Suppliers.memoize(this::convert);
        remaining = new AtomicLong(properties.getLimit());
        stopTime = System.nanoTime() + properties.getDuration().toNanos();
        scenario = new PublishScenario(properties);
        builder = PublishRequest.builder().scenario(scenario);
        Assert.state(properties.getRetry().getMaxAttempts() > 0, "maxAttempts must be positive");
    }

    @Override
    public List<PublishRequest> next(int count) {
        if (count <= 0) {
            count = 1;
        }

        long left = remaining.getAndAdd(-count);
        long actual = Math.min(left, count);
        if (actual <= 0) {
            throw new ScenarioException(scenario, "Reached publish limit");
        }

        if (stopTime - System.nanoTime() <= 0) {
            throw new ScenarioException(scenario, "Reached publish duration");
        }

        List<PublishRequest> publishRequests = new ArrayList<>();
        for (long i = 0; i < actual; i++) {
            var transaction = transactionSupplier
                    .get()
                    .get()
                    .setMaxAttempts((int) properties.getRetry().getMaxAttempts())
                    .setTransactionMemo(scenario.getMemo());

            PublishRequest publishRequest = builder.receipt(shouldGenerate(properties.getReceiptPercent()))
                    .sendRecord(shouldGenerate(properties.getRecordPercent()))
                    .timestamp(Instant.now())
                    .transaction(transaction)
                    .build();
            publishRequests.add(publishRequest);
        }

        return publishRequests;
    }

    @Override
    public Flux<PublishScenario> scenarios() {
        return Flux.just(scenario);
    }

    private TransactionSupplier<?> convert() {
        Map<String, String> convertedProperties = expressionConverter.convert(properties.getProperties());
        Map<String, Object> correctedProperties = scenarioPropertiesAggregator.aggregateProperties(convertedProperties);
        TransactionSupplier<?> supplier = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .convertValue(correctedProperties, properties.getType().getSupplier());

        validateSupplier(supplier);

        return supplier;
    }

    private void validateSupplier(TransactionSupplier<?> supplier) {
        try (var validatorFactory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            var validations = validator.validate(supplier);

            if (!validations.isEmpty()) {
                throw new ConstraintViolationException(validations);
            }

            if (supplier instanceof AccountDeleteTransactionSupplier accountDeleteTransactionSupplier) {
                String accountId = accountDeleteTransactionSupplier.getTransferAccountId();
                accountDeleteTransactionSupplier.setTransferAccountId(accountIdValidator.validate(accountId));
            }
        }
    }

    private boolean shouldGenerate(double expectedPercent) {
        return RANDOM.nextDouble() < expectedPercent;
    }
}
