package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.contract.ContractTransaction;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Named
public class ContractTransactionBuilder extends AbstractEntityBuilder<ContractTransaction, ContractTransaction.ContractTransactionBuilder> {
    private final ContractResultBuilder contractResultBuilder;
    private final ContractStateChangeBuilder contractStateChangeBuilder;
    private final ContractLogBuilder contractLogBuilder;

    public ContractTransactionBuilder(ContractResultBuilder contractResultBuilder, ContractStateChangeBuilder contractStateChangeBuilder, ContractLogBuilder contractLogBuilder) {
        this.contractResultBuilder = contractResultBuilder;
        this.contractStateChangeBuilder = contractStateChangeBuilder;
        this.contractLogBuilder = contractLogBuilder;
    }

    @Override
    protected ContractTransaction.ContractTransactionBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return ContractTransaction.builder();
    }

    @Override
    protected ContractTransaction getFinalEntity(ContractTransaction.ContractTransactionBuilder builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        var contractResults = specSetup.contractresults();
        var contractStateChanges = specSetup.contractStateChanges();
        var contractLogs = specSetup.contractlogs();

        List<Map<String, Object>> options = new ArrayList<>();

        if (contractResults != null) {
            for (var result : contractResults) {
                var context = new SpecBuilderContext(result);
                var builder = contractResultBuilder.getEntityBuilder(context);
                customizeWithSpec(builder, result, context);
                var contractResult = contractResultBuilder.getFinalEntity(builder, result);
                options.add(getContractTransaction(contractResult.getContractId(), contractResult.getPayerAccountId().getId(), contractResult.getConsensusTimestamp()));
            }
        }

        if (contractStateChanges != null) {
            for (var result : contractStateChanges) {
                var context = new SpecBuilderContext(result);
                var builder = contractStateChangeBuilder.getEntityBuilder(context);
                customizeWithSpec(builder, result, context);
                var stateChange = contractStateChangeBuilder.getFinalEntity(builder, result);
                options.add(getContractTransaction(stateChange.getContractId(), stateChange.getPayerAccountId().getId(), stateChange.getConsensusTimestamp()));
            }
        }

        if (contractLogs != null) {
            for (var result : contractLogs) {
                var context = new SpecBuilderContext(result);
                var builder = contractLogBuilder.getEntityBuilder(context);
                customizeWithSpec(builder, result, context);
                var contractLog = contractLogBuilder.getFinalEntity(builder, result);
                options.add(getContractTransaction(contractLog.getContractId().getId(), contractLog.getPayerAccountId().getId(), contractLog.getConsensusTimestamp()));
            }
        }

        return () -> options.stream()
                .collect(Collectors.groupingBy(item -> item.getOrDefault("consensus_timestamp", DEFAULT_CONSENSUS_TIMESTAMP)))
                .entrySet()
                .stream()
                .map(entry -> getTransactionsForEntry(entry.getKey(), entry.getValue()))
                .flatMap(Collection::stream)
                .toList();
    }

    private Map<String, Object> getContractTransaction(long entityId, long payerAccountId, long consensusTimestamp) {
        return new HashMap<>(Map.of("entity_id", entityId,
                "consensus_timestamp", consensusTimestamp,
                "payer_account_id", payerAccountId));
    }

    private Collection<Map<String, Object>> getTransactionsForEntry(Object timestamp, List<Map<String, Object>> candidates) {
        Map<Object, Map<String, Object>> transactions = new HashMap<>();
        Set<Long> contractIds = new HashSet<>();

        for (var candidate : candidates) {
            var payerAccountId = candidate.get("payer_account_id");
            var contractId = candidate.get("entity_id");
            candidate.put("contract_ids", contractIds);

            var payerCopy = new HashMap<>(candidate);
            payerCopy.put("entity_id", payerAccountId);

            transactions.putIfAbsent(payerAccountId, payerCopy);
            transactions.putIfAbsent(contractId, candidate);

            contractIds.add((Long) contractId);
            contractIds.add((Long) payerAccountId);
        }

        return transactions.values();
    }
}
