// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.hiero.mirror.web3.repository.projections.ContractTransactionHashLookup;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps {@code contract_transaction_hash} rows to {@link ContractTransactionHashLookup}. Spring Data JDBC cannot
 * instantiate an interface projection for a string based query, so the rows are mapped explicitly.
 */
public final class ContractTransactionHashLookupRowMapper implements RowMapper<ContractTransactionHashLookup> {

    @Override
    public ContractTransactionHashLookup mapRow(final ResultSet rs, final int rowNum) throws SQLException {
        return new Row(
                rs.getLong("consensus_timestamp"),
                rs.getLong("entity_id"),
                rs.getLong("payer_account_id"),
                rs.getObject("transaction_result", Integer.class));
    }

    private record Row(
            long consensusTimestamp,
            long entityId,
            long payerAccountId,
            @Nullable Integer transactionResult) implements ContractTransactionHashLookup {

        @Override
        public long getConsensusTimestamp() {
            return consensusTimestamp;
        }

        @Override
        public long getEntityId() {
            return entityId;
        }

        @Override
        public long getPayerAccountId() {
            return payerAccountId;
        }

        @Override
        public @Nullable Integer getTransactionResult() {
            return transactionResult;
        }
    }
}
