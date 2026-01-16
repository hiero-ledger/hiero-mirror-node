// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.balance;

import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.importer.exception.ImporterException;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
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
