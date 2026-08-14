// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.junit.jupiter.api.Test;

final class OpcodeContextTest {

    private static OpcodeRequest request() {
        return new OpcodeRequest(new TransactionIdParameter(EntityId.EMPTY, Instant.EPOCH), false, false, false);
    }

    private static OpcodesProperties propertiesWithMaxOpcodes(final int maxOpcodes) {
        final var properties = new OpcodesProperties();
        properties.setMaxOpcodes(maxOpcodes);
        return properties;
    }

    @Test
    void twoArgConstructorAppliesDefaults() {
        final var context = new OpcodeContext(request(), 0);
        final var defaults = new OpcodesProperties();

        assertThat(context.getMaxOpcodes()).isEqualTo(defaults.getMaxOpcodes());
        assertThat(context.getMaxMemoryWordsPerOpcode()).isEqualTo(defaults.getMaxMemoryWordsPerOpcode());
        assertThat(context.getMaxStackItemsPerOpcode()).isEqualTo(defaults.getMaxStackItemsPerOpcode());
        assertThat(context.getMaxStorageEntriesPerOpcode()).isEqualTo(defaults.getMaxStorageEntriesPerOpcode());
    }

    @Test
    void addOpcodesRetainsAllOpcodesBelowTheConfiguredCap() {
        final int maxOpcodes = 5;
        final var context = new OpcodeContext(request(), 0, propertiesWithMaxOpcodes(maxOpcodes));
        final var opcode = new Opcode();

        for (int i = 0; i < maxOpcodes; i++) {
            context.addOpcodes(opcode);
        }

        assertThat(context.getOpcodes())
                .hasSize(maxOpcodes)
                .noneMatch(o -> OpcodeContext.TRUNCATED_OP.equals(o.getOp()));
        // Exactly at the cap: the next opcode would be dropped, but nothing has been truncated yet
        assertThat(context.isAtCapacity()).isTrue();
        assertThat(context.isTruncated()).isFalse();
        assertThat(context.getExecutedOpcodes()).isEqualTo(maxOpcodes);
    }

    @Test
    void addTruncatedOpcodeAppendsMarkerOnceAndCountsWithoutStoring() {
        final int maxOpcodes = 3;
        final var context = new OpcodeContext(request(), 0, propertiesWithMaxOpcodes(maxOpcodes));
        final var opcode = new Opcode();

        // Fill to capacity through the normal path
        for (int i = 0; i < maxOpcodes; i++) {
            context.addOpcodes(opcode);
        }
        // Then account for dropped opcodes directly, as the tracer does once isAtCapacity() is true
        context.addTruncatedOpcode();
        context.addTruncatedOpcode();

        final var opcodes = context.getOpcodes();
        assertThat(opcodes).hasSize(maxOpcodes + 1); // cap + single marker
        assertThat(opcodes.get(opcodes.size() - 1).getOp()).isEqualTo(OpcodeContext.TRUNCATED_OP);
        assertThat(context.isTruncated()).isTrue();
        assertThat(context.getExecutedOpcodes()).isEqualTo(maxOpcodes + 2L);
    }

    @Test
    void addOpcodesTruncatesAtConfiguredCapAndAppendsMarker() {
        final int maxOpcodes = 5;
        final var context = new OpcodeContext(request(), 0, propertiesWithMaxOpcodes(maxOpcodes));
        final var opcode = new Opcode();

        for (int i = 0; i < maxOpcodes + 10; i++) {
            context.addOpcodes(opcode);
        }

        final var opcodes = context.getOpcodes();
        // The configured cap plus exactly one truncation marker
        assertThat(opcodes).hasSize(maxOpcodes + 1);

        final var marker = opcodes.get(opcodes.size() - 1);
        assertThat(marker.getOp()).isEqualTo(OpcodeContext.TRUNCATED_OP);
        assertThat(marker.getReason()).contains("truncated");
        assertThat(marker.getMemory()).isEmpty();
        assertThat(marker.getStack()).isEmpty();
        assertThat(marker.getStorage()).isEmpty();

        // Dropped opcodes are still counted so the caller can report how much was omitted
        assertThat(context.isTruncated()).isTrue();
        assertThat(context.getExecutedOpcodes()).isEqualTo(maxOpcodes + 10L);
    }
}
