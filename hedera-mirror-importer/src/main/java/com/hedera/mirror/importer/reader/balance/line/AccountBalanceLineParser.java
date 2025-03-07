// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader.balance.line;

import com.hedera.mirror.common.domain.balance.AccountBalance;

public interface AccountBalanceLineParser {
    String INVALID_BALANCE = "Invalid account balance line: ";

    AccountBalance parse(String line, long consensusTimestamp);
}
