// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.staking;

import org.hiero.mirror.importer.parser.record.transactionhandler.NodeStakeUpdatedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;

@Async
public interface EntityStakeCalculator {

    @TransactionalEventListener(classes = NodeStakeUpdatedEvent.class)
    void calculate();

    @EventListener(classes = ApplicationReadyEvent.class)
    default void calculateOnApplicationReady() {
        calculate();
    }
}
