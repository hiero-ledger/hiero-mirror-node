// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "hiero.mirror.web3.opcode.tracer")
@Data
@Validated
public class OpcodesProperties {
    private boolean enabled = true;

    /**
     * Maximum number of opcodes recorded per trace request. Once reached the trace is truncated with a
     * single marker opcode and further opcodes are dropped. Raise this to trace larger transactions at the cost of a
     * higher worst-case heap footprint. Note the worst-case trace size is roughly {@code maxOpcodes} ×
     * {@code maxMemoryWordsPerOpcode} × 32 bytes, so raising either knob compounds the other.
     */
    @Positive
    private int maxOpcodes = 20_000;

    /**
     * Maximum number of 32-byte EVM memory words captured per opcode (default 2048 = 64 KB). Opcodes whose memory
     * exceeds this are captured up to this many words, the remainder is omitted.
     */
    @Positive
    private int maxMemoryWordsPerOpcode = 2_048;

    /**
     * Maximum number of stack items captured per opcode. The EVM already bounds the stack to 1024 items, so this is a
     * defensive cap; stacks larger than this are captured up to this many (top-most) items and the remainder is omitted.
     */
    @Positive
    private int maxStackItemsPerOpcode = 1_024;

    /**
     * Maximum number of storage entries captured per opcode. Storage capture reflects the cumulative transaction
     * storage, which grows with every touched slot, so without this cap a storage-heavy transaction would retain a
     * per-opcode snapshot that grows with the whole trace. Entries beyond this limit are omitted.
     */
    @Positive
    private int maxStorageEntriesPerOpcode = 1_024;
}
