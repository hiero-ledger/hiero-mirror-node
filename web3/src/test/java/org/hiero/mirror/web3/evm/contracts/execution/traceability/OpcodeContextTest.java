// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

    private static Opcode opcode(final int memory, final int stack, final int storage) {
        final Map<String, String> storageMap = new HashMap<>();
        for (int i = 0; i < storage; i++) {
            storageMap.put("key" + i, "value");
        }
        return new Opcode()
                .memory(Collections.nCopies(memory, "0x00"))
                .stack(Collections.nCopies(stack, "0x00"))
                .storage(storageMap);
    }

    @Test
    void constructorReadsLimitsFromProperties() {
        final var context = new OpcodeContext(request(), 0, new OpcodesProperties());
        final var defaults = new OpcodesProperties();

        assertThat(context.getMaxOpcodes()).isEqualTo(defaults.getMaxOpcodes());
        assertThat(context.getMaxMemoryWords()).isEqualTo(defaults.getMaxMemoryWords());
        assertThat(context.getMaxStack()).isEqualTo(defaults.getMaxStack());
        assertThat(context.getMaxStorage()).isEqualTo(defaults.getMaxStorage());
    }

    @Test
    void truncatesWhenCumulativeMemoryBudgetReached() {
        // maxMemoryWords=10; each opcode captures 4 memory words, so the 4th offered opcode is dropped
        final var properties = new OpcodesProperties();
        properties.setMaxMemoryWords(10);
        final var context = new OpcodeContext(request(), 0, properties);

        for (int i = 0; i < 5; i++) {
            context.addOpcodes(opcode(4, 0, 0));
        }

        final var opcodes = context.getOpcodes();
        assertThat(opcodes).hasSize(4); // 3 recorded + single truncation marker
        assertThat(opcodes.getLast().getOp()).isEqualTo(OpcodeContext.TRUNCATED_OP);
        assertThat(context.getCapturedMemoryWords()).isEqualTo(12L);
        assertThat(context.isTruncated()).isTrue();
        assertThat(context.getExecutedOpcodes()).isEqualTo(5L);
    }

    @Test
    void truncatesWhenCumulativeStackBudgetReached() {
        final var properties = new OpcodesProperties();
        properties.setMaxStack(10);
        final var context = new OpcodeContext(request(), 0, properties);

        for (int i = 0; i < 5; i++) {
            context.addOpcodes(opcode(0, 4, 0));
        }

        assertThat(context.getOpcodes()).hasSize(4);
        assertThat(context.getCapturedStack()).isEqualTo(12L);
        assertThat(context.isTruncated()).isTrue();
    }

    @Test
    void truncatesWhenCumulativeStorageBudgetReached() {
        final var properties = new OpcodesProperties();
        properties.setMaxStorage(10);
        final var context = new OpcodeContext(request(), 0, properties);

        for (int i = 0; i < 5; i++) {
            context.addOpcodes(opcode(0, 0, 4));
        }

        assertThat(context.getOpcodes()).hasSize(4);
        assertThat(context.getCapturedStorage()).isEqualTo(12L);
        assertThat(context.isTruncated()).isTrue();
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
    void addOpcodesAppendsMarkerOnceAndCountsWithoutStoring() {
        final int maxOpcodes = 3;
        final var context = new OpcodeContext(request(), 0, propertiesWithMaxOpcodes(maxOpcodes));
        final var opcode = new Opcode();

        // Fill to capacity through the normal path
        for (int i = 0; i < maxOpcodes; i++) {
            context.addOpcodes(opcode);
        }
        // Then account for dropped opcodes with a null opcode, as the tracer does once isAtCapacity() is true
        context.addOpcodes(null);
        context.addOpcodes(null);

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
