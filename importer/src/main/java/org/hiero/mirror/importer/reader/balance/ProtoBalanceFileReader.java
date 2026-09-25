// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance;

import static org.hiero.mirror.common.util.DomainUtils.MAX_SIZE_FILE;
import static org.hiero.mirror.common.util.DomainUtils.parseProtobuf;

import com.hedera.services.stream.proto.AllAccountBalances;
import com.hedera.services.stream.proto.SingleAccountBalances;
import jakarta.inject.Named;
import java.util.List;
import lombok.CustomLog;
import org.apache.commons.codec.digest.DigestUtils;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;

@CustomLog
@Named
public class ProtoBalanceFileReader implements BalanceFileReader {

    private static final String FILE_EXTENSION = "pb";

    @Override
    public boolean supports(StreamFileData streamFileData) {
        return FILE_EXTENSION.equals(
                streamFileData.getStreamFilename().getExtension().getName());
    }

    @Override
    public AccountBalanceFile read(StreamFileData streamFileData) {
        final var bytes = streamFileData.getDecompressedBytes();
        final var allAccountBalances = parseProtobuf(bytes, AllAccountBalances::parseFrom, MAX_SIZE_FILE);

        if (!allAccountBalances.hasConsensusTimestamp()) {
            throw new InvalidStreamFileException("Missing required consensusTimestamp field");
        }

        final long consensusTimestamp = DomainUtils.timestampInNanosMax(allAccountBalances.getConsensusTimestamp());
        final var items = allAccountBalances.getAllAccountsList().stream()
                .map(ab -> toAccountBalance(consensusTimestamp, ab))
                .toList();

        final var accountBalanceFile = new AccountBalanceFile();
        accountBalanceFile.setBytes(streamFileData.getBytes());
        accountBalanceFile.setConsensusTimestamp(consensusTimestamp);
        accountBalanceFile.setFileHash(DigestUtils.sha384Hex(bytes));
        accountBalanceFile.setItems(items);
        accountBalanceFile.setLoadStart(streamFileData.getStreamFilename().getTimestamp());
        accountBalanceFile.setName(streamFileData.getFilename());
        return accountBalanceFile;
    }

    private AccountBalance toAccountBalance(long consensusTimestamp, SingleAccountBalances balances) {
        EntityId accountId = EntityId.of(balances.getAccountID());
        List<TokenBalance> tokenBalances = balances.getTokenUnitBalancesList().stream()
                .map(tokenBalance -> {
                    EntityId tokenId = EntityId.of(tokenBalance.getTokenId());
                    TokenBalance.Id id = new TokenBalance.Id(consensusTimestamp, accountId, tokenId);
                    return new TokenBalance(tokenBalance.getBalance(), id);
                })
                .toList();
        return new AccountBalance(
                balances.getHbarBalance(), tokenBalances, new AccountBalance.Id(consensusTimestamp, accountId));
    }
}
