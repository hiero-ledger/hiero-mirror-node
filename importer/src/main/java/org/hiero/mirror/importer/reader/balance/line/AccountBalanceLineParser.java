// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance.line;

import org.hiero.mirror.common.domain.balance.AccountBalance;

public interface AccountBalanceLineParser {
    String INVALID_BALANCE = "Invalid account balance line: ";

    AccountBalance parse(String line, long consensusTimestamp);
}
