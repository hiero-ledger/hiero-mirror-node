// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.NANOS_PER_SECOND;

import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hederahashgraph.api.proto.java.Account;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Bytecode;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.File;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.SlotKey;
import com.hederahashgraph.api.proto.java.SlotValue;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;

final class InitialStateReaderTest {

    private final InitialStateReader reader = new InitialStateReaderImpl();
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    @Test
    void empty() {
        assertThat(reader.read(Collections.emptyList())).isEqualTo(new RecordFile.InitialState());
    }

    @Test
    void read() {
        // given
        // first StateChanges - accounts, some are smart contract accounts
        final var expectedEntities = new ArrayList<Entity>();
        final var expectedContracts = new ArrayList<Contract>();
        final var expectedFileDatum = new ArrayList<FileData>();

        final var accountId = recordItemBuilder.accountId();
        final var accountTimestamp = recordItemBuilder.timestamp();
        final var contractAccountId1 = recordItemBuilder.accountId();
        final var contractAccountId2 = recordItemBuilder.accountId();
        final var accountsStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(accountTimestamp)
                .addStateChanges(accountStateChange(accountId, accountTimestamp, false, expectedEntities))
                .addStateChanges(accountStateChange(contractAccountId1, accountTimestamp, true, expectedEntities))
                .addStateChanges(accountStateChange(contractAccountId2, accountTimestamp, true, expectedEntities))
                .build();

        // second StateChanges - bytecode for the smart contract account from the first StateChanges
        final var bytecodeStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(accountTimestamp)
                .addStateChanges(bytecodeStateChange(contractAccountId1, expectedContracts))
                .addStateChanges(bytecodeStateChange(contractAccountId2, expectedContracts))
                .build();

        // third StateChanges - 2 file map update
        final var fileTimestamp = recordItemBuilder.timestamp();
        final var filesStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(fileTimestamp)
                .addStateChanges(fileStateChange(fileTimestamp, expectedEntities, expectedFileDatum))
                .addStateChanges(fileStateChange(fileTimestamp, expectedEntities, expectedFileDatum))
                .build();

        // last StateChanges - contract storage slots for the smart contract account from the first StateChanges
        final var storageStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(accountTimestamp)
                .addStateChanges(storageStateChange(contractAccountId1))
                .addStateChanges(storageStateChange(contractAccountId2))
                .build();

        // when
        final var initialState = reader.read(
                List.of(accountsStateChanges, bytecodeStateChanges, filesStateChanges, storageStateChanges));

        // then
        // accounts and file produce entities; contract storage slots are not processed
        assertThat(initialState)
                .satisfies(
                        s -> assertThat(s.entities()).containsExactlyInAnyOrderElementsOf(expectedEntities),
                        s -> assertThat(s.contracts()).containsExactlyInAnyOrderElementsOf(expectedContracts),
                        s -> assertThat(s.fileDatum()).containsExactlyInAnyOrderElementsOf(expectedFileDatum));
    }

    private StateChange accountStateChange(
            final AccountID accountId,
            final Timestamp timestamp,
            final boolean smartContract,
            final Collection<Entity> expectedEntities) {
        final long autoRenewPeriod = recordItemBuilder.timestamp().getSeconds();
        final long expirationSecond = recordItemBuilder.timestamp().getSeconds();
        final long balance = recordItemBuilder.id();
        final var key = !smartContract
                ? recordItemBuilder.key()
                : Key.newBuilder().setContractID(toContractID(accountId)).build();
        final long consensusTimestamp = DomainUtils.timestampInNanosMax(timestamp);
        final boolean receiverSignatureRequired = recordItemBuilder.id() % 3 == 0;

        final var entity = EntityId.of(accountId).toEntity();
        entity.setAutoRenewPeriod(autoRenewPeriod);
        entity.setBalance(balance);
        entity.setBalanceTimestamp(consensusTimestamp);
        entity.setDeleted(false);
        entity.setExpirationTimestamp(expirationSecond * NANOS_PER_SECOND);
        entity.setKey(key.toByteArray());
        entity.setMaxAutomaticTokenAssociations(0);
        entity.setMemo(accountId.toString());
        entity.setReceiverSigRequired(receiverSignatureRequired);
        entity.setTimestampLower(consensusTimestamp);
        entity.setType(!smartContract ? EntityType.ACCOUNT : EntityType.CONTRACT);

        final var account = Account.newBuilder()
                .setAccountId(accountId)
                .setAutoRenewSeconds(autoRenewPeriod)
                .setExpirationSecond(expirationSecond)
                .setKey(key)
                .setMemo(accountId.toString())
                .setReceiverSigRequired(receiverSignatureRequired)
                .setSmartContract(smartContract)
                .setTinybarBalance(balance);

        if (recordItemBuilder.id() % 3 == 0) {
            final var autoRenewAccountId = recordItemBuilder.accountId();
            account.setAutoRenewAccountId(autoRenewAccountId);
            entity.setAutoRenewAccountId(EntityId.of(autoRenewAccountId).getId());
        }

        expectedEntities.add(entity);

        return StateChange.newBuilder()
                .setStateId(StateIdentifier.STATE_ID_ACCOUNTS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setAccountIdKey(accountId))
                        .setValue(MapChangeValue.newBuilder().setAccountValue(account)))
                .build();
    }

    private StateChange bytecodeStateChange(
            final AccountID contractAccountId, final Collection<Contract> expectedContracts) {
        final var contractId = toContractID(contractAccountId);
        final byte[] bytecode = recordItemBuilder.randomBytes(512);
        expectedContracts.add(Contract.builder()
                .id(EntityId.of(contractAccountId).getId())
                .runtimeBytecode(bytecode)
                .build());
        return StateChange.newBuilder()
                .setStateId(StateIdentifier.STATE_ID_BYTECODE_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setContractIdKey(contractId))
                        .setValue(MapChangeValue.newBuilder()
                                .setBytecodeValue(Bytecode.newBuilder().setCode(DomainUtils.fromBytes(bytecode)))))
                .build();
    }

    private StateChange fileStateChange(
            final Timestamp timestamp,
            final Collection<Entity> expectedEntities,
            final Collection<FileData> expectedFileDatum) {
        final byte[] content = recordItemBuilder.randomBytes(1024);
        final long expirationSecond = recordItemBuilder.timestamp().getSeconds();
        final var fileId = recordItemBuilder.fileId();
        final long consensusTimestamp = DomainUtils.timestampInNanosMax(timestamp);

        final var entityId = EntityId.of(fileId);
        final var entity = entityId.toEntity();
        entity.setDeleted(false);
        entity.setExpirationTimestamp(expirationSecond * NANOS_PER_SECOND);
        entity.setMemo(fileId.toString());
        entity.setTimestampLower(consensusTimestamp);
        entity.setType(EntityType.FILE);

        final var file = File.newBuilder()
                .setFileId(fileId)
                .setContents(DomainUtils.fromBytes(content))
                .setExpirationSecond(expirationSecond)
                .setMemo(fileId.toString());
        if (recordItemBuilder.id() % 3 == 1) {
            final var key =
                    KeyList.newBuilder().addKeys(recordItemBuilder.key()).build();
            entity.setKey(key.toByteArray());
            file.setKeys(key);
        }

        expectedEntities.add(entity);
        expectedFileDatum.add(FileData.builder()
                .consensusTimestamp(consensusTimestamp)
                .fileData(content)
                .entityId(entityId)
                .transactionType(TransactionType.FILEUPDATE.getProtoId())
                .build());

        return StateChange.newBuilder()
                .setStateId(StateIdentifier.STATE_ID_FILES_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setValue(MapChangeValue.newBuilder().setFileValue(file)))
                .build();
    }

    private StateChange storageStateChange(final AccountID contractAccountId) {
        final var slotKey = SlotKey.newBuilder()
                .setContractID(toContractID(contractAccountId))
                .setKey(recordItemBuilder.bytes(32))
                .build();
        final var slotValue =
                SlotValue.newBuilder().setValue(recordItemBuilder.bytes(128)).build();
        return StateChange.newBuilder()
                .setStateId(StateIdentifier.STATE_ID_STORAGE_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setSlotKeyKey(slotKey))
                        .setValue(MapChangeValue.newBuilder().setSlotValueValue(slotValue)))
                .build();
    }

    private ContractID toContractID(final AccountID accountId) {
        return ContractID.newBuilder()
                .setContractNum(accountId.getAccountNum())
                .setRealmNum(accountId.getRealmNum())
                .setShardNum(accountId.getShardNum())
                .build();
    }
}
