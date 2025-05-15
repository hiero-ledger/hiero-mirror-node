// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import java.io.Serial;
import org.springframework.context.ApplicationEvent;

public class NodeStakeUpdatedEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 7947202040212120506L;

    /**
     * Create a new {@code AccountBalanceFileParsedEvent}.
     *
     * @param source the object on which the event initially occurred or with which the event is associated (never
     *               {@code null})
     */
    public NodeStakeUpdatedEvent(Object source) {
        super(source);
    }
}
