// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.ScheduleTests;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.GetScheduleInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * This test class validates the correct results for getting schedule info for a token create transaction via smart contract calls .
 */
class ContractCallGetScheduleInfoTest extends AbstractContractCallScheduleTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getFungibleCreateScheduleInfoNonExisting(final Boolean isFungible) {
        // Cannot get schedule info for non-existing schedule
        // Given
        final var entity = domainBuilder.entityId();
        final var nonExistingAddress = toAddress(entity).toHexString();
        final var contract = testWeb3jService.deploy(GetScheduleInfo::deploy);
        // When
        final var functionCall = isFungible
                ? contract.call_getFungibleCreateTokenInfo(nonExistingAddress)
                : contract.call_getNonFungibleCreateTokenInfo(nonExistingAddress);

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
        final var maxSupply = 1000L;
        // Build Schedule Token create transaction body in bytes
        final var scheduleTokenCreateTransactionBodyBytes = buildScheduledTokenCreateTransactionBody(
                treasuryAccount, tokenName, tokenSymbol, TokenType.FUNGIBLE_COMMON, 500L, maxSupply);
        // Persist schedule
        final var schedule = domainBuilder
                .schedule()
                .customize(e -> e.scheduleId(scheduleEntity.toEntityId().getId())
                        .transactionBody(scheduleTokenCreateTransactionBodyBytes)
                        .payerAccountId(payerAccount.toEntityId())
                        .creatorAccountId(payerAccount.toEntityId()))
                .persist();

        // When
        final var entityId = EntityId.of(schedule.getScheduleId());
        final var functionCall = contract.call_getFungibleCreateTokenInfo(getAddressFromEntityId(entityId));
        // Then
        final var functionCallResult = functionCall.send();
        assertThat(functionCallResult.component1()).isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.getNumber()));
        assertThat(functionCallResult.component2().tokenInfo.token.name).isEqualTo(tokenName);
        assertThat(functionCallResult.component2().tokenInfo.token.symbol).isEqualTo(tokenSymbol);
        assertThat(functionCallResult.component2().tokenInfo.token.treasury)
                .isEqualTo(getAddressFromEntityId(treasuryAccount.toEntityId()));
        assertThat(functionCallResult.component2().tokenInfo.token.maxSupply).isEqualTo(BigInteger.valueOf(maxSupply));
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
        final var maxSupply = 1000L;
        // Build Schedule Token create transaction body in bytes
        final var scheduleTokenCreateTransactionBodyBytes = buildScheduledTokenCreateTransactionBody(
                treasuryAccount, tokenName, tokenSymbol, TokenType.NON_FUNGIBLE_UNIQUE, 0L, maxSupply);
        // Persist schedule
        final var schedule = domainBuilder
                .schedule()
                .customize(e -> e.scheduleId(scheduleEntity.toEntityId().getId())
                        .transactionBody(scheduleTokenCreateTransactionBodyBytes)
                        .payerAccountId(payerAccount.toEntityId())
                        .creatorAccountId(payerAccount.toEntityId()))
                .persist();
        // When
        final var entityId = EntityId.of(schedule.getScheduleId());
        final var functionCall = contract.call_getNonFungibleCreateTokenInfo(getAddressFromEntityId(entityId));
        // Then
        final var functionCallResult = functionCall.send();
        assertThat(functionCallResult.component1()).isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.getNumber()));
        assertThat(functionCallResult.component2().tokenInfo.token.name).isEqualTo(tokenName);
        assertThat(functionCallResult.component2().tokenInfo.token.symbol).isEqualTo(tokenSymbol);
        assertThat(functionCallResult.component2().tokenInfo.token.treasury)
                .isEqualTo(getAddressFromEntityId(treasuryAccount.toEntityId()));
        assertThat(functionCallResult.component2().tokenInfo.token.maxSupply).isEqualTo(BigInteger.valueOf(maxSupply));
    }

    /**
     * Build Scheduled Token Create transaction body in bytes
     *
     * @param treasuryAccount The treasury account of the token
     * @param name The name of the token
     * @param symbol The symbol of the token
     * @param tokenType Token type, either Fungible or Non-Fungible
     * @param initialSupply The initial supply of the token
     * @param maxSupply the max supply of the token
     * @return The schedule transaction body for token create transaction in bytes
     */
    private byte[] buildScheduledTokenCreateTransactionBody(
            Entity treasuryAccount,
            String name,
            String symbol,
            TokenType tokenType,
            long initialSupply,
            long maxSupply) {

        final var treasuryAccountId = AccountID.newBuilder()
                .shardNum(treasuryAccount.getShard())
                .realmNum(treasuryAccount.getRealm())
                .accountNum(treasuryAccount.getNum())
                .build();

        final var tokenCreateTransactionBody = TokenCreateTransactionBody.newBuilder()
                .tokenType(tokenType)
                .name(name)
                .symbol(symbol)
                .supplyType(TokenSupplyType.FINITE)
                .expiry(Timestamp.newBuilder().seconds(10L).build())
                .treasury(treasuryAccountId)
                .autoRenewAccount(AccountID.newBuilder()
                        .accountNum(treasuryAccount.getNum())
                        .build())
                .initialSupply(initialSupply)
                .maxSupply(maxSupply)
                .build();

        final var scheduleTransactionBody = SchedulableTransactionBody.newBuilder()
                .tokenCreation(tokenCreateTransactionBody)
                .build();

        return CommonPbjConverters.asBytes(SchedulableTransactionBody.PROTOBUF, scheduleTransactionBody);
    }
}
