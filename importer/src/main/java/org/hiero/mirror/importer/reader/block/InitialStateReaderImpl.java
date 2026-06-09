// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.hiero.mirror.common.util.DomainUtils.NANOS_PER_SECOND;

import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import jakarta.inject.Named;
import java.util.Collection;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;

@Named
final class InitialStateReaderImpl implements InitialStateReader {

    @Override
    public RecordFile.InitialState read(Collection<StateChanges> initialStateChanges) {
        final var initialState = new RecordFile.InitialState();
        for (final var stateChanges : initialStateChanges) {
            final long consensusTimestamp = DomainUtils.timestampInNanosMax(stateChanges.getConsensusTimestamp());
            for (final var stateChange : stateChanges.getStateChangesList()) {
                final var mapUpdate = stateChange.getMapUpdate();
                switch (stateChange.getStateId()) {
                    case StateIdentifier.STATE_ID_ACCOUNTS_VALUE ->
                        readAccount(consensusTimestamp, initialState, mapUpdate);
                    case StateIdentifier.STATE_ID_BYTECODE_VALUE ->
                        readBytecode(consensusTimestamp, initialState, mapUpdate);
                    case StateIdentifier.STATE_ID_FILES_VALUE -> readFile(consensusTimestamp, initialState, mapUpdate);
                    // There are also contract storage slots statechanges in mainnet's genesis WRB. They are not
                    // processed here because storage slots are mutable and the contract storage state is later synced
                    // through sidecar migration.
                }
            }
        }

        return initialState;
    }

    private void readAccount(
            final long consensusTimestamp,
            final RecordFile.InitialState initialState,
            final MapUpdateChange mapUpdateChange) {
        final var account = mapUpdateChange.getValue().getAccountValue();
        final var entity = EntityId.of(account.getAccountId()).toEntity();

        // Account properties added later (e.g., alias, allowances, staking metadata, etc.) are not parsed
        entity.setAutoRenewPeriod(account.getAutoRenewSeconds());
        entity.setBalance(account.getTinybarBalance());
        entity.setBalanceTimestamp(consensusTimestamp);
        entity.setDeleted(account.getDeleted());
        entity.setExpirationTimestamp(account.getExpirationSecond() * NANOS_PER_SECOND);
        entity.setMaxAutomaticTokenAssociations(account.getMaxAutoAssociations());
        entity.setMemo(account.getMemo());
        entity.setReceiverSigRequired(account.getReceiverSigRequired());
        entity.setTimestampLower(consensusTimestamp);
        entity.setType(account.getSmartContract() ? EntityType.CONTRACT : EntityType.ACCOUNT);

        if (account.hasAutoRenewAccountId()) {
            entity.setAutoRenewAccountId(
                    EntityId.of(account.getAutoRenewAccountId()).getId());
        }

        if (account.hasKey()) {
            entity.setKey(account.getKey().toByteArray());
        }

        initialState.entities().add(entity);
    }

    private void readBytecode(
            final long consensusTimestamp,
            final RecordFile.InitialState initialState,
            final MapUpdateChange mapUpdateChange) {
        final var entityId = EntityId.of(mapUpdateChange.getKey().getContractIdKey());
        final byte[] bytecode = DomainUtils.toBytes(
                mapUpdateChange.getValue().getBytecodeValue().getCode());
        final var contract = Contract.builder()
                .id(entityId.getId())
                .runtimeBytecode(bytecode)
                .build();
        initialState.contracts().add(contract);
    }

    private void readFile(
            final long consensusTimestamp,
            final RecordFile.InitialState initialState,
            final MapUpdateChange mapUpdateChange) {
        final var file = mapUpdateChange.getValue().getFileValue();
        final var entityId = EntityId.of(file.getFileId());
        final var entity = entityId.toEntity();
        entity.setDeleted(file.getDeleted());
        entity.setExpirationTimestamp(file.getExpirationSecond() * NANOS_PER_SECOND);
        entity.setMemo(file.getMemo());
        entity.setTimestampLower(consensusTimestamp);
        entity.setType(EntityType.FILE);

        if (file.hasKeys()) {
            entity.setKey(file.getKeys().toByteArray());
        }

        final var fileData = new FileData();
        fileData.setConsensusTimestamp(consensusTimestamp);
        fileData.setEntityId(entityId);
        fileData.setFileData(DomainUtils.toBytes(file.getContents()));
        fileData.setTransactionType(TransactionType.FILEUPDATE.getProtoId());

        initialState.entities().add(entity);
        initialState.fileDatum().add(fileData);
    }
}
