// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.balance;

import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.parser.StreamFileListener;

public interface BalanceStreamFileListener extends StreamFileListener<AccountBalanceFile> {}
