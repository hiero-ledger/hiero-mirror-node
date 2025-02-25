// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.balance;

import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;

@Named
@Primary
@RequiredArgsConstructor
public class CompositeBalanceStreamFileListener implements BalanceStreamFileListener {

    private final List<BalanceStreamFileListener> listeners;
    private final AccountBalanceFileRepository accountBalanceFileRepository;

    @Override
    public void onEnd(AccountBalanceFile streamFile) throws ImporterException {
        accountBalanceFileRepository.save(streamFile);
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onEnd(streamFile);
        }
    }
}
