// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository.upsert;

import jakarta.inject.Named;
import java.text.MessageFormat;

@Named
public class DissociateTokenTransferUpsertQueryGenerator implements UpsertQueryGenerator {

    private static final String FINAL_TABLE_NAME = "token_transfer";
    private static final String TEMP_TABLE_NAME = "dissociate_token_transfer";

    private static final String INSERT_SQL = MessageFormat.format("""
                    with nft_history as (
                      insert into nft_history (account_id, created_timestamp, delegating_spender, deleted, metadata,
                        serial_number, spender, token_id, timestamp_range)
                      select
                        n.account_id,
                        created_timestamp,
                        delegating_spender,
                        deleted,
                        metadata,
                        serial_number,
                        spender,
                        n.token_id,
                        int8range(lower(timestamp_range), tdt.consensus_timestamp)
                      from nft n
                      join {0} tdt on tdt.account_id = n.account_id and tdt.token_id = n.token_id
                    ), dissociated_nft as (
                      update nft
                        set account_id = null,
                            delegating_spender = null,
                            deleted = true,
                            spender = null,
                            timestamp_range = int8range(tdt.consensus_timestamp, null)
                      from {0} tdt
                      where nft.token_id = tdt.token_id and nft.account_id = tdt.account_id and nft.deleted is false
                      returning nft.token_id
                    ), nft_token as (
                      select distinct token_id from dissociated_nft
                    ), nft_transfer as (
                      select consensus_timestamp, jsonb_agg(jsonb_build_object(
                        ''is_approval'', false,
                        ''receiver_account_id'', null,
                        ''sender_account_id'', tdt.account_id,
                        ''serial_number'', tdt.amount,
                        ''token_id'', tdt.token_id
                      ) order by tdt.token_id asc, tdt.amount asc) as transfer
                      from {0} tdt
                      join nft_token on nft_token.token_id = tdt.token_id
                      group by tdt.consensus_timestamp
                    ), update_transaction as (
                      update transaction t
                      set nft_transfer = transfer
                      from nft_transfer nt
                      where nt.consensus_timestamp = t.consensus_timestamp
                    )
                    insert into {1}
                    select tdt.*
                    from {0} tdt
                    left join nft_token nt on nt.token_id = tdt.token_id
                    where nt.token_id is null
                    """, TEMP_TABLE_NAME, FINAL_TABLE_NAME);

    @Override
    public String getFinalTableName() {
        return FINAL_TABLE_NAME;
    }

    @Override
    public String getUpsertQuery() {
        return INSERT_SQL;
    }

    @Override
    public String getTemporaryTableName() {
        return TEMP_TABLE_NAME;
    }
}
