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
        // maxMemoryWords=10; each opcode captures 4 words, so the 3rd would reach 12 and is dropped before recording
        final var properties = new OpcodesProperties();
        properties.setMaxMemoryWords(10);
        final var context = new OpcodeContext(request(), 0, properties);

        for (int i = 0; i < 5; i++) {
            context.addOpcodes(opcode(4, 0, 0));
        }

        final var opcodes = context.getOpcodes();
        assertThat(opcodes).hasSize(3); // 2 recorded + single truncation marker
        assertThat(opcodes.getLast().getOp()).isEqualTo(OpcodeContext.TRUNCATED_OP);
        // Never overshoots the budget: stays at 8, not 12
        assertThat(context.getCapturedMemoryWords()).isEqualTo(8L);
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

        assertThat(context.getOpcodes()).hasSize(3);
        assertThat(context.getCapturedStack()).isEqualTo(8L);
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

        assertThat(context.getOpcodes()).hasSize(3);
        assertThat(context.getCapturedStorage()).isEqualTo(8L);
        assertThat(context.isTruncated()).isTrue();
    }

    @Test
    void doesNotOvershootBudgetWhenSingleOpcodeWouldExceedIt() {
        final var properties = new OpcodesProperties();
        properties.setMaxMemoryWords(10);
        final var context = new OpcodeContext(request(), 0, properties);

        context.addOpcodes(opcode(4, 0, 0)); // fits: 4 <= 10
        context.addOpcodes(opcode(1000, 0, 0)); // would reach 1004, dropped before recording rather than overshooting

        assertThat(context.getCapturedMemoryWords()).isEqualTo(4L);
        final var opcodes = context.getOpcodes();
        assertThat(opcodes).hasSize(2); // the fitting opcode + truncation marker
        assertThat(opcodes.getLast().getOp()).isEqualTo(OpcodeContext.TRUNCATED_OP);
        assertThat(context.isTruncated()).isTrue();
    }

    @Test
    void addOpcodesRetainsAllOpcodesUpToTheReservedCap() {
        final int maxOpcodes = 5;
        final var context = new OpcodeContext(request(), 0, propertiesWithMaxOpcodes(maxOpcodes));
        final var opcode = new Opcode();

        // One slot is reserved for a potential truncation marker, so up to maxOpcodes - 1 real opcodes are retained
        for (int i = 0; i < maxOpcodes - 1; i++) {
            context.addOpcodes(opcode);
        }

        assertThat(context.getOpcodes())
                .hasSize(maxOpcodes - 1)
                .noneMatch(o -> OpcodeContext.TRUNCATED_OP.equals(o.getOp()));
        // The reserved slot means we report at capacity once maxOpcodes - 1 opcodes are recorded, nothing truncated yet
        assertThat(context.isAtCapacity()).isTrue();
        assertThat(context.isTruncated()).isFalse();
        assertThat(context.getExecutedOpcodes()).isEqualTo(maxOpcodes - 1L);
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
        // Opcode-count budget is binding: the marker fills the reserved slot, so the list stays at maxOpcodes
        assertThat(opcodes).hasSize(maxOpcodes);
        assertThat(opcodes.getLast().getOp()).isEqualTo(OpcodeContext.TRUNCATED_OP);
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
        // The returned list never exceeds maxOpcodes: one slot is reserved for the marker
        assertThat(opcodes).hasSize(maxOpcodes);

        final var marker = opcodes.getLast();
        assertThat(marker.getOp()).isEqualTo(OpcodeContext.TRUNCATED_OP);
        assertThat(marker.getReason()).contains("truncated");
        assertThat(marker.getMemory()).isEmpty();
        assertThat(marker.getStack()).isEmpty();
        assertThat(marker.getStorage()).isEmpty();
        // Only one marker is present
        assertThat(opcodes)
                .filteredOn(o -> OpcodeContext.TRUNCATED_OP.equals(o.getOp()))
                .hasSize(1);

        // Dropped opcodes are still counted so the caller can report how much was omitted
        assertThat(context.isTruncated()).isTrue();
        assertThat(context.getExecutedOpcodes()).isEqualTo(maxOpcodes + 10L);
    }
}
