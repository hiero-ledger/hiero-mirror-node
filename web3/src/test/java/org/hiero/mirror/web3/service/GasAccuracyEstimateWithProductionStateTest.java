// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.common.domain.transaction.TransactionType.FILECREATE;
import static org.hiero.mirror.common.util.DomainUtils.NANOS_PER_SECOND;
import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.longValueOf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import jakarta.annotation.Resource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class GasAccuracyEstimateWithProductionStateTest extends AbstractContractCallServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final V0490FileSchema FILE_SCHEMA = new V0490FileSchema();
    private static final JsonNode DYNAMIC_TRANSFER_TO_STAKING_REWARDS_ACCOUNT =
            loadFixture("transfer-to-staking-reward-account-previewnet-state.json");
    private static final byte[] PREVIEWNET_SIMPLE_FEE_SCHEDULE =
            simpleFeeScheduleBytes("previewnet-simpleFeesSchedules.json");
    private static final JsonNode CONTRACT_CREATE_WITH_INITIAL_VALUE =
            loadFixture("contract-create-with-value-testnet-state.json");

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Test
    void estimateGasForDynamicBytesEventWithHbarTransfer() {
        persistPreviewnetState(DYNAMIC_TRANSFER_TO_STAKING_REWARDS_ACCOUNT);
        final var request = DYNAMIC_TRANSFER_TO_STAKING_REWARDS_ACCOUNT.get("request");
        final long gasUsed =
                DYNAMIC_TRANSFER_TO_STAKING_REWARDS_ACCOUNT.get("gas_used").asLong();
        final var block = BlockType.of(request.get("block").asText());
        final long estimated = longValueOf.applyAsLong(contractExecutionService.processCall(estimateParameters(
                block,
                request.get("data").asText(),
                Address.fromHexString(request.get("from").asText()),
                Address.fromHexString(request.get("to").asText()),
                request.path("value").asLong(0L),
                request.get("gas").asLong())));

        assertThat(gasUsed).isEqualTo(34_187L);
        assertThat(estimated).isEqualTo(36_073L);
    }

    @Test
    void estimateGasForContractCreationWithValue() {
        persistCreateState(CONTRACT_CREATE_WITH_INITIAL_VALUE);
        final var request = CONTRACT_CREATE_WITH_INITIAL_VALUE.get("request");
        final long gasUsed = CONTRACT_CREATE_WITH_INITIAL_VALUE.get("gas_used").asLong();
        final var block = BlockType.of(request.get("block").asText());
        final long estimated = longValueOf.applyAsLong(contractExecutionService.processCall(estimateParameters(
                block,
                request.get("data").asText(),
                Address.fromHexString(request.get("from").asText()),
                Address.ZERO,
                request.path("value").asLong(0L),
                request.get("gas").asLong())));

        assertThat(gasUsed).isEqualTo(165_444L);
        assertThat(estimated).isEqualTo(179_776L);
    }

    private ContractExecutionParameters estimateParameters(
            final BlockType blockType,
            final String dataHex,
            final Address senderAddress,
            final Address receiverAddress,
            final long value,
            final long gas) {
        return ContractExecutionParameters.builder()
                .block(blockType)
                .callData(hexToBytes(dataHex))
                .callType(ETH_ESTIMATE_GAS)
                .gas(gas)
                .gasPrice(0L)
                .isEstimate(true)
                .isStatic(false)
                .receiver(receiverAddress)
                .sender(senderAddress)
                .value(value)
                .build();
    }

    private void persistPreviewnetState(final JsonNode fixture) {
        persistNetworkState(fixture, PREVIEWNET_SIMPLE_FEE_SCHEDULE);
        final var block = fixture.get("block");
        final long stateTimestamp = timestampToNanos(block.get("timestamp_from").asText()) - 1L;
        for (final var contract : fixture.get("contracts")) {
            persistContract(contract, stateTimestamp);
        }
    }

    private void persistCreateState(final JsonNode fixture) {
        persistNetworkState(fixture, simpleFeeScheduleBytes());
    }

    private void persistNetworkState(final JsonNode fixture, final byte[] simpleFeeSchedule) {
        final var block = fixture.get("block");
        final long consensusStart = timestampToNanos(block.get("timestamp_from").asText());
        final long consensusEnd = timestampToNanos(block.get("timestamp_to").asText());
        final long stateTimestamp = consensusStart - 1L;

        persistSystemFile(systemEntity.exchangeRateFile(), exchangeRateBytes(fixture.get("exchange_rate")), 1L);
        persistSystemFile(
                systemEntity.feeScheduleFile(),
                FILE_SCHEMA
                        .genesisFeeSchedules(evmProperties.getVersionedConfiguration())
                        .toByteArray(),
                2L);
        persistSystemFile(systemEntity.simpleFeeScheduleFile(), simpleFeeSchedule, 3L);
        persistSystemFile(systemEntity.throttleDefinitionFile(), throttleDefinitionBytes(), 4L);

        jdbcTemplate.update("""
                update entity
                set created_timestamp = ?, timestamp_range = int8range(cast(? as bigint), null)
                """, stateTimestamp, stateTimestamp);
        persistAccountBalance(treasuryEntity.toEntityId(), treasuryEntity.getBalance(), stateTimestamp);

        domainBuilder
                .recordFile()
                .customize(f -> f.index(block.get("number").asLong())
                        .consensusStart(consensusStart)
                        .consensusEnd(consensusEnd)
                        .hash(block.get("hash").asText())
                        .previousHash(block.get("previous_hash").asText()))
                .persist();

        for (final var account : fixture.get("accounts")) {
            final var entityId = persistAccount(account, stateTimestamp);
            persistAccountBalance(entityId, account.path("balance").asLong(0L), stateTimestamp);
        }
    }

    private void persistContract(final JsonNode contract, final long stateTimestamp) {
        final var contractId = configuredEntityId(contract.get("account").asText());
        final var evmAddress = hexToBytes(contract.get("evm_address").asText());
        final long balance = contract.path("balance").asLong(0L);
        domainBuilder
                .entity(contractId)
                .customize(e -> e.type(EntityType.CONTRACT)
                        .alias(null)
                        .evmAddress(evmAddress)
                        .delegationAddress(null)
                        .balance(balance)
                        .ethereumNonce(contract.path("nonce").asLong(1L))
                        .createdTimestamp(stateTimestamp)
                        .timestampRange(Range.atLeast(stateTimestamp))
                        .deleted(false)
                        .receiverSigRequired(false)
                        .maxAutomaticTokenAssociations(-1))
                .persist();
        domainBuilder
                .contract()
                .customize(c -> c.id(contractId.getId())
                        .runtimeBytecode(loadHexResource(
                                contract.get("runtime_bytecode_file").asText())))
                .persist();
        persistAccountBalance(contractId, balance, stateTimestamp);
        persistHistoricalContractSlots(contractId, contract.path("slots"), stateTimestamp);
    }

    private void persistHistoricalContractSlots(
            final EntityId contractId, final JsonNode slots, final long stateTimestamp) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        final var rows = new ArrayList<Object[]>(slots.size());
        final var fields = slots.fields();
        while (fields.hasNext()) {
            final var slot = fields.next();
            final var key =
                    Bytes32.wrap(hexToBytes(slot.getKey())).trimLeadingZeros().toArrayUnsafe();
            rows.add(new Object[] {
                stateTimestamp,
                contractId.getId(),
                key,
                hexToBytes(slot.getValue().asText()),
                contractId.getId()
            });
        }
        jdbcTemplate.batchUpdate("""
                insert into contract_state_change
                    (consensus_timestamp, contract_id, slot, value_read, payer_account_id)
                values (?, ?, ?, ?, ?)
                """, rows);
    }

    private EntityId persistAccount(final JsonNode account, final long createdTimestamp) {
        final var entityId = configuredEntityId(account.get("account").asText());
        final var evmAddress = account.has("evm_address")
                ? hexToBytes(account.get("evm_address").asText())
                : null;
        final var rawKey = hexToBytes(account.path("key").asText(""));
        final byte[] key = rawKey.length == 33
                ? Key.newBuilder()
                        .setECDSASecp256K1(ByteString.copyFrom(rawKey))
                        .build()
                        .toByteArray()
                : rawKey;
        domainBuilder
                .entity(entityId)
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .alias(evmAddress)
                        .evmAddress(evmAddress)
                        .key(key)
                        .balance(account.path("balance").asLong(0L))
                        .createdTimestamp(createdTimestamp)
                        .timestampRange(Range.atLeast(createdTimestamp))
                        .deleted(false)
                        .receiverSigRequired(false)
                        .maxAutomaticTokenAssociations(-1))
                .persist();
        return entityId;
    }

    private EntityId configuredEntityId(final String entityId) {
        return domainBuilder.entityNum(EntityId.of(entityId).getNum());
    }

    private void persistAccountBalance(final EntityId entityId, final long balance, final long timestamp) {
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.id(new AccountBalance.Id(timestamp, entityId)).balance(balance))
                .persist();
    }

    private void persistSystemFile(final EntityId fileId, final byte[] contents, final long consensusTimestamp) {
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(fileId)
                        .fileData(contents)
                        .transactionType(FILECREATE.getProtoId())
                        .consensusTimestamp(consensusTimestamp))
                .persist();
    }

    private static byte[] simpleFeeScheduleBytes() {
        return simpleFeeScheduleBytes("simpleFeesSchedules.json");
    }

    private static byte[] simpleFeeScheduleBytes(final String filename) {
        try (final var in = resourceStream(filename)) {
            final var schedule = FeeSchedule.JSON.parse(Bytes.wrap(in.readAllBytes()));
            return FeeSchedule.PROTOBUF.toBytes(schedule).toByteArray();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] loadHexResource(final String filename) {
        try (final var in = resourceStream(filename)) {
            return hexToBytes(new String(in.readAllBytes()).strip());
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to load hex resource " + filename, e);
        }
    }

    private static byte[] throttleDefinitionBytes() {
        try (final var in = resourceStream("testnet-throttles.json")) {
            return V0490FileSchema.parseThrottleDefinitions(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] exchangeRateBytes(final JsonNode rates) {
        return ExchangeRateSet.newBuilder()
                .setCurrentRate(toExchangeRate(rates.get("current_rate")))
                .setNextRate(toExchangeRate(rates.get("next_rate")))
                .build()
                .toByteArray();
    }

    private static ExchangeRate toExchangeRate(final JsonNode rate) {
        return ExchangeRate.newBuilder()
                .setCentEquiv(rate.get("cent_equivalent").asInt())
                .setHbarEquiv(rate.get("hbar_equivalent").asInt())
                .setExpirationTime(TimestampSeconds.newBuilder()
                        .setSeconds(rate.get("expiration_time").asLong()))
                .build();
    }

    private static JsonNode loadFixture(final String filename) {
        try (final var in = resourceStream(filename)) {
            return OBJECT_MAPPER.readTree(in);
        } catch (final Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static InputStream resourceStream(final String filename) {
        final var in = GasAccuracyEstimateWithProductionStateTest.class.getResourceAsStream(
                "/gas-estimate-accuracy/" + filename);
        if (in == null) {
            throw new IllegalStateException("Missing resource /gas-estimate-accuracy/" + filename);
        }
        return in;
    }

    private static long timestampToNanos(final String timestamp) {
        final var parts = timestamp.split("\\.");
        return Long.parseLong(parts[0]) * NANOS_PER_SECOND + Long.parseLong(parts[1]);
    }
}
