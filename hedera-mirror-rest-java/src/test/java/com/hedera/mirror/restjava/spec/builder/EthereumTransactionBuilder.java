// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
public class EthereumTransactionBuilder
        extends AbstractEntityBuilder<EthereumTransaction, EthereumTransaction.EthereumTransactionBuilder> {

    @Override
    protected EthereumTransaction.EthereumTransactionBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return EthereumTransaction.builder()
                .consensusTimestamp(187654000123456L)
                .data(HEX_FORMAT.parseHex("0000000000"))
                .gasLimit(1000000L)
                .gasPrice(HEX_FORMAT.parseHex("4a817c80"))
                .hash(HEX_FORMAT.parseHex("0000000000000000000000000000000000000000000000000000000000000123"))
                .maxGasAllowance(10000L)
                .nonce(1L)
                .payerAccountId(EntityId.of(5001L))
                .recoveryId(1)
                .signatureR(HEX_FORMAT.parseHex("d693b532a80fed6392b428604171fb32fdbf953728a3a7ecc7d4062b1652c042"))
                .signatureS(HEX_FORMAT.parseHex("24e9c602ac800b983b035700a14b23f78a253ab762deab5dc27e3555a750b354"))
                .signatureV(HEX_FORMAT.parseHex("1b"))
                .toAddress(HEX_FORMAT.parseHex("0000000000000000000000000000000000001389"))
                .type(2)
                .value(new byte[1]);
    }

    @Override
    protected EthereumTransaction getFinalEntity(
            EthereumTransaction.EthereumTransactionBuilder builder, Map<String, Object> entityAttributes) {
        return builder.build();
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::ethereumtransactions;
    }
}
