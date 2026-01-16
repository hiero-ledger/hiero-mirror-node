// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance;

import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.reader.StreamFileReader;

/**
 * Reads an account balance file, parses the header to get the consensus timestamp, and extracts
 * <code>AccountBalance</code> objects, one such object per valid account balance line.
 */
public interface BalanceFileReader extends StreamFileReader<AccountBalanceFile, AccountBalance> {

    boolean supports(StreamFileData streamFileData);
}
