// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import java.io.Serial;
import lombok.Value;
import org.springframework.context.ApplicationEvent;

/**
 * Should be published when RegisteredNodeCreate, RegisteredNodeUpdate, or RegisteredNodeDelete
 * transaction is processed. Should trigger cache invalidation for block node discovery.
 */
@Value
public class RegisteredNodeChangedEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    public RegisteredNodeChangedEvent(Object source) {
        super(source);
    }
}
