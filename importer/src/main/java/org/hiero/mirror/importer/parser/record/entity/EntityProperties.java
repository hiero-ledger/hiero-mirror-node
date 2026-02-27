// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.hiero.mirror.common.domain.transaction.TransactionType.CONSENSUSSUBMITMESSAGE;
import static org.hiero.mirror.common.domain.transaction.TransactionType.SCHEDULECREATE;
import static org.hiero.mirror.common.domain.transaction.TransactionType.SCHEDULESIGN;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;
import java.util.Set;
import lombok.Data;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConfigurationProperties("hiero.mirror.importer.parser.record.entity")
@Validated
public class EntityProperties {

    @NotNull
    @Valid
    private PersistProperties persist;

    @Autowired
    public EntityProperties(SystemEntity systemEntity) {
        this.persist = new PersistProperties(systemEntity);
    }

    @Data
    @Validated
    public static class PersistProperties {

        private boolean claims = false;

        private boolean contracts = true;

        private boolean contractResults = true;

        private boolean contractTransaction = true;

        private boolean contractTransactionHash = true;

        private boolean cryptoTransferAmounts = true;

        private boolean entityHistory = true;

        private boolean entityNftTransactions = false;

        /**
         * A set of entity ids to exclude from entity_transaction table
         */
        @NotNull
        private Set<EntityId> entityTransactionExclusion;

        private boolean entityTransactions = false;

        private boolean ethereumTransactions = true;

        private boolean files = true;

        private boolean itemizedTransfers = false;

        private boolean pendingReward = true;

        private boolean schedules = true;

        private boolean syntheticContractLogs = true;

        private boolean syntheticContractLogEvmAddressLookup = true;

        private boolean syntheticContractResults = false;

        private boolean systemFiles = true;

        private boolean tokens = true;

        private boolean tokenAirdrops = true;

        private boolean topics = true;

        private boolean topicMessageLookups = false;

        private boolean trackAllowance = true;

        private boolean trackBalance = true;

        private boolean trackNonce = true;

        private boolean transactionHash = true;

        /**
         * A set of transaction types to persist transaction hash for. If empty and transactionHash is true, transaction
         * hash of all transaction types will be persisted
         */
        @NotNull
        private Set<TransactionType> transactionHashTypes = EnumSet.complementOf(EnumSet.of(CONSENSUSSUBMITMESSAGE));

        /**
         * If configured the mirror node will store the raw transaction bytes on the transaction table
         */
        private boolean transactionBytes = false;

        /**
         * If configured the mirror node will store the raw transaction record bytes on the transaction table.
         */
        private boolean transactionRecordBytes = false;

        @NotNull
        private Set<TransactionType> transactionSignatures = EnumSet.of(SCHEDULECREATE, SCHEDULESIGN);

        public PersistProperties(SystemEntity systemEntity) {
            this.entityTransactionExclusion = Set.of(
                    systemEntity.feeCollectionAccount(),
                    systemEntity.networkAdminFeeAccount(),
                    systemEntity.nodeRewardAccount(),
                    systemEntity.stakingRewardAccount());
        }

        public boolean isTokenAirdrops() {
            return tokenAirdrops && tokens;
        }

        public boolean shouldPersistEntityTransaction(EntityId entityId) {
            return entityTransactions && !EntityId.isEmpty(entityId) && !entityTransactionExclusion.contains(entityId);
        }

        public boolean shouldPersistEntityNftTransaction(EntityId entityId, RecordItem recordItem) {
            if (!entityNftTransactions || EntityId.isEmpty(entityId) || entityTransactionExclusion.contains(entityId)) {
                return false;
            }

            int transactionTypeValue = recordItem.getTransactionType();
            TransactionType transactionType = TransactionType.of(transactionTypeValue);

            return transactionType == TransactionType.CRYPTOTRANSFER
                    || transactionType == TransactionType.TOKENMINT
                    || transactionType == TransactionType.TOKENBURN
                    || transactionType == TransactionType.TOKENWIPE
                    || transactionType == TransactionType.TOKENUPDATE;
        }

        public boolean shouldPersistTransactionHash(TransactionType transactionType) {
            return transactionHash
                    && (transactionHashTypes.isEmpty() || transactionHashTypes.contains(transactionType));
        }
    }
}
