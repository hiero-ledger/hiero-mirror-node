// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import java.io.Serial;
import lombok.Value;
import org.springframework.context.ApplicationEvent;

@Value
public class RecordFileParsedEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 4130926676946978039L;

    private final long consensusEnd;

    public RecordFileParsedEvent(Object source, long consensusEnd) {
        super(source);
        this.consensusEnd = consensusEnd;
    }
}
