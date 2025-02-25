// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader.balance;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.reader.StreamFileReader;

/**
 * Reads an account balance file, parses the header to get the consensus timestamp, and extracts
 * <code>AccountBalance</code> objects, one such object per valid account balance line.
 */
public interface BalanceFileReader extends StreamFileReader<AccountBalanceFile, AccountBalance> {

    boolean supports(StreamFileData streamFileData);
}
