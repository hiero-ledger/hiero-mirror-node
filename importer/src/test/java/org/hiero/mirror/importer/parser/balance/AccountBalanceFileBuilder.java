// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.balance;

import static org.hiero.mirror.importer.domain.StreamFilename.FileType.DATA;

import jakarta.inject.Named;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.springframework.util.Assert;

@Named
@RequiredArgsConstructor
public class AccountBalanceFileBuilder {

    private final DomainBuilder domainBuilder;

    public Builder accountBalanceFile() {
        return accountBalanceFile(domainBuilder.timestamp());
    }

    public Builder accountBalanceFile(long consensusTimestamp) {
        return new Builder(consensusTimestamp);
    }

    public class Builder {

        private final List<AccountBalance> accountBalanceList = new ArrayList<>();
        private final long consensusTimestamp;

        private Builder(long consensusTimestamp) {
            this.consensusTimestamp = consensusTimestamp;
        }

        public Builder accountBalance(AccountBalance accountBalance) {
            accountBalance.getId().setConsensusTimestamp(consensusTimestamp);
            for (var tokenBalance : accountBalance.getTokenBalances()) {
                tokenBalance.getId().setConsensusTimestamp(consensusTimestamp);
            }
            accountBalanceList.add(accountBalance);
            return this;
        }

        public AccountBalanceFile build() {
            Assert.notEmpty(accountBalanceList, "Must contain at least one account balance");

            Instant instant = Instant.ofEpochSecond(0, consensusTimestamp);
            String filename = StreamFilename.getFilename(StreamType.BALANCE, DATA, instant);
            return AccountBalanceFile.builder()
                    .bytes(domainBuilder.bytes(16))
                    .count((long) accountBalanceList.size())
                    .consensusTimestamp(consensusTimestamp)
                    .fileHash(Hex.encodeHexString(domainBuilder.bytes(48)))
                    .items(accountBalanceList)
                    .loadStart(domainBuilder.timestamp())
                    .name(filename)
                    .nodeId(0L)
                    .build();
        }
    }
}
