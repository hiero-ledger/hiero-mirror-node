// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance.line;

import com.google.common.base.Splitter;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.exception.InvalidDatasetException;

@Named
public class AccountBalanceLineParserV1 implements AccountBalanceLineParser {

    private static final Splitter SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

    /**
     * Parses an account balance line to extract shard, realm, account, and balance. If the shard matches
     * systemShardNum, creates and returns an {@code AccountBalance} entity object. The account balance line should be
     * in the format of "shard,realm,account,balance"
     *
     * @param line               The account balance line
     * @param consensusTimestamp The consensus timestamp of the account balance line
     * @return {@code AccountBalance} entity object
     * @throws InvalidDatasetException if the line is malformed or the shard does not match {@code systemShardNum}
     */
    @Override
    public AccountBalance parse(String line, long consensusTimestamp) {
        try {
            if (line == null) {
                throw new InvalidDatasetException("Null line cannot be parsed");
            }
            List<String> parts = SPLITTER.splitToList(line);
            if (parts.size() != 4) {
                throw new InvalidDatasetException(INVALID_BALANCE + line);
            }

            long shardNum = Long.parseLong(parts.get(0));
            int realmNum = Integer.parseInt(parts.get(1));
            int accountNum = Integer.parseInt(parts.get(2));
            long balance = Long.parseLong(parts.get(3));
            if (shardNum < 0 || realmNum < 0 || accountNum < 0 || balance < 0) {
                throw new InvalidDatasetException(INVALID_BALANCE + line);
            }

            if (shardNum != 0) {
                throw new InvalidDatasetException(String.format(
                        "Invalid account balance line: %s. Expect shard (0), got shard (%d)", line, shardNum));
            }

            return new AccountBalance(
                    balance,
                    Collections.emptyList(),
                    new AccountBalance.Id(consensusTimestamp, EntityId.of(shardNum, realmNum, accountNum)));
        } catch (NumberFormatException ex) {
            throw new InvalidDatasetException(INVALID_BALANCE + line, ex);
        }
    }
}
