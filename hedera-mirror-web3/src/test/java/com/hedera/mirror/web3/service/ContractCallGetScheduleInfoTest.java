// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.generated.GetScheduleInfo;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ContractCallGetScheduleInfoTest extends AbstractContractCallServiceHistoricalTest {

    @Test
    void getFungibleCreateScheduleInfoNonExisting() throws Exception {
        // Cannot get schedule info for non-existing schedule
        // Given
        final var entity = domainBuilder.entityId();
        final var nonExistingAddress = toAddress(entity).toHexString();
        final var contract = testWeb3jService.deploy(GetScheduleInfo::deploy);
        // When
        final var functionCall = contract.call_getFungibleCreateTokenInfo(nonExistingAddress);
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void getNFTCreateScheduleInfoNonExisting() throws Exception {
        // Cannot get schedule info for non-existing schedule
        // Given
        final var entity = domainBuilder.entityId();
        final var nonExistingAddress = toAddress(entity).toHexString();
        final var contract = testWeb3jService.deploy(GetScheduleInfo::deploy);
        // When
        final var functionCall = contract.call_getNonFungibleCreateTokenInfo(nonExistingAddress);
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void getFungibleCreateScheduleInfo() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(GetScheduleInfo::deploy);
        final var payerAccount = accountEntityPersist();
        final var treasuryAccount = accountEntityPersist();
        final var scheduleEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.SCHEDULE))
                .persist();
        final var tokenName = "Fungible-Token";
        final var tokenSymbol = "FUNG";
        final var maxSupply = 1000;
        // Create Token create transaction body
        final var expiry = Timestamp.newBuilder().seconds(10L).build();
        final var tokenCreateTransactionBody = TokenCreateTransactionBody.newBuilder()
                .tokenType(TokenType.FUNGIBLE_COMMON)
                .name(tokenName)
                .supplyType(TokenSupplyType.FINITE)
                .expiry(expiry)
                .treasury(AccountID.newBuilder()
                        .shardNum(treasuryAccount.getShard())
                        .realmNum(treasuryAccount.getRealm())
                        .accountNum(treasuryAccount.getNum())
                        .build())
                .symbol(tokenSymbol)
                .autoRenewAccount(AccountID.newBuilder()
                        .accountNum(treasuryAccount.getNum())
                        .build())
                .initialSupply(500L)
                .maxSupply(maxSupply)
                .build();
        // Create Schedule transaction body
        final var scheduleTransactionBody = SchedulableTransactionBody.newBuilder()
                .tokenCreation(tokenCreateTransactionBody)
                .build();
        final var bytes = CommonPbjConverters.asBytes(SchedulableTransactionBody.PROTOBUF, scheduleTransactionBody);
        // Persist schedule
        final var schedule = domainBuilder
                .schedule()
                .customize(e -> e.scheduleId(scheduleEntity.toEntityId().getId())
                        .transactionBody(bytes)
                        .payerAccountId(payerAccount.toEntityId())
                        .creatorAccountId(payerAccount.toEntityId()))
                .persist();

        // When
        final var entityId = EntityId.of(schedule.getScheduleId());
        final var functionCall = contract.call_getFungibleCreateTokenInfo(getAddressFromEntityId(entityId));
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var functionCallResult = functionCall.send();
            assertThat(functionCallResult.component1())
                    .isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.getNumber()));
            assertThat(functionCallResult.component2().tokenInfo.token.name).isEqualTo(tokenName);
            assertThat(functionCallResult.component2().tokenInfo.token.symbol).isEqualTo(tokenSymbol);
            assertThat(functionCallResult.component2().tokenInfo.token.treasury)
                    .isEqualTo(getAddressFromEntityId(treasuryAccount.toEntityId()));
            assertThat(functionCallResult.component2().tokenInfo.token.maxSupply)
                    .isEqualTo(BigInteger.valueOf(maxSupply));

        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void getNonFungibleCreateScheduleInfo() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(GetScheduleInfo::deploy);
        final var payerAccount = accountEntityPersist();
        final var treasuryAccount = accountEntityPersist();
        final var scheduleEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.SCHEDULE))
                .persist();
        final var tokenName = "Non-Fungible-Token";
        final var tokenSymbol = "NFT";
        final var maxSupply = 1000;
        // Create Token create transaction body
        final var expiry = Timestamp.newBuilder().seconds(10L).build();
        final var tokenCreateTransactionBody = TokenCreateTransactionBody.newBuilder()
                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                .name(tokenName)
                .supplyType(TokenSupplyType.FINITE)
                .expiry(expiry)
                .treasury(AccountID.newBuilder()
                        .shardNum(treasuryAccount.getShard())
                        .realmNum(treasuryAccount.getRealm())
                        .accountNum(treasuryAccount.getNum())
                        .build())
                .symbol(tokenSymbol)
                .autoRenewAccount(AccountID.newBuilder()
                        .accountNum(treasuryAccount.getNum())
                        .build())
                .initialSupply(0L)
                .maxSupply(maxSupply)
                .build();
        // Create Schedule transaction body
        final var scheduleTransactionBody = SchedulableTransactionBody.newBuilder()
                .tokenCreation(tokenCreateTransactionBody)
                .build();
        final var bytes = CommonPbjConverters.asBytes(SchedulableTransactionBody.PROTOBUF, scheduleTransactionBody);
        // Persist schedule
        final var schedule = domainBuilder
                .schedule()
                .customize(e -> e.scheduleId(scheduleEntity.toEntityId().getId())
                        .transactionBody(bytes)
                        .payerAccountId(payerAccount.toEntityId())
                        .creatorAccountId(payerAccount.toEntityId()))
                .persist();
        // When
        final var entityId = EntityId.of(schedule.getScheduleId());
        final var functionCall = contract.call_getNonFungibleCreateTokenInfo(getAddressFromEntityId(entityId));
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var functionCallResult = functionCall.send();
            assertThat(functionCallResult.component1())
                    .isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.getNumber()));
            assertThat(functionCallResult.component2().tokenInfo.token.name).isEqualTo(tokenName);
            assertThat(functionCallResult.component2().tokenInfo.token.symbol).isEqualTo(tokenSymbol);
            assertThat(functionCallResult.component2().tokenInfo.token.treasury)
                    .isEqualTo(getAddressFromEntityId(treasuryAccount.toEntityId()));
            assertThat(functionCallResult.component2().tokenInfo.token.maxSupply)
                    .isEqualTo(BigInteger.valueOf(maxSupply));
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }
}
