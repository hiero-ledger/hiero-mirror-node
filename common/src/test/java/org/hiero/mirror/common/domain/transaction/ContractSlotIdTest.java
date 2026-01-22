// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HookEntityId;
import com.hederahashgraph.api.proto.java.HookId;
import org.junit.jupiter.api.Test;

class ContractSlotIdTest {

    private static final long HOOK_SYSTEM_CONTRACT_NUM = 365L;

    @Test
    void constructorValidatesExactlyOneFieldSet() {
        var contractId = ContractID.newBuilder().setContractNum(100).build();
        var hookId = HookId.newBuilder().setHookId(1).build();

        // Valid: contractId set, hookId null
        assertThat(new ContractSlotId(contractId, null)).isNotNull();

        // Valid: hookId set, contractId null
        assertThat(new ContractSlotId(null, hookId)).isNotNull();

        // Invalid: both null
        assertThatThrownBy(() -> new ContractSlotId(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Exactly one of contractId or hookId must be set");

        // Invalid: both set
        assertThatThrownBy(() -> new ContractSlotId(contractId, hookId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Exactly one of contractId or hookId must be set");
    }

    @Test
    void ofRegularContractWithoutHook() {
        // Regular contract (not 365) without executed hook
        var contractId = ContractID.newBuilder().setContractNum(100).build();

        var slotId = ContractSlotId.of(contractId, null);

        assertThat(slotId).isNotNull();
        assertThat(slotId.contractId()).isEqualTo(contractId);
        assertThat(slotId.hookId()).isNull();
    }

    @Test
    void ofRegularContractWithHook() {
        // Regular contract (not 365) WITH executed hook - hook should be IGNORED
        var contractId = ContractID.newBuilder().setContractNum(100).build();
        var hookId = HookId.newBuilder()
                .setHookId(1)
                .setEntityId(HookEntityId.newBuilder()
                        .setAccountId(AccountID.newBuilder().setAccountNum(200)))
                .build();

        var slotId = ContractSlotId.of(contractId, hookId);

        // Hook should be ignored for regular contracts
        assertThat(slotId).isNotNull();
        assertThat(slotId.contractId()).isEqualTo(contractId);
        assertThat(slotId.hookId()).isNull();
    }

    @Test
    void ofHookSystemContractWithHook() {
        // Hook system contract (365) with executed hook
        var contractId =
                ContractID.newBuilder().setContractNum(HOOK_SYSTEM_CONTRACT_NUM).build();
        var hookId = HookId.newBuilder()
                .setHookId(1)
                .setEntityId(HookEntityId.newBuilder()
                        .setAccountId(AccountID.newBuilder().setAccountNum(200)))
                .build();

        var slotId = ContractSlotId.of(contractId, hookId);

        // Should be hook storage
        assertThat(slotId).isNotNull();
        assertThat(slotId.contractId()).isNull();
        assertThat(slotId.hookId()).isEqualTo(hookId);
    }

    @Test
    void ofHookSystemContractWithoutHook_ReturnsNull() {
        // Hook system contract (365) WITHOUT executed hook - INVALID STATE
        var contractId =
                ContractID.newBuilder().setContractNum(HOOK_SYSTEM_CONTRACT_NUM).build();

        var slotId = ContractSlotId.of(contractId, null);

        // Should return null for invalid state
        assertThat(slotId).isNull();
    }

    @Test
    void ofRegularContractWithNullHookId() {
        // Regular contract with null hookId - should work fine
        var contractId = ContractID.newBuilder().setContractNum(100).build();

        var slotId = ContractSlotId.of(contractId, null);

        assertThat(slotId).isNotNull();
        assertThat(slotId.contractId()).isEqualTo(contractId);
        assertThat(slotId.hookId()).isNull();
    }

    @Test
    void ofNullContractIdWithNullHookId_ThrowsException() {
        // Null contractId and null hookId will violate the invariant
        assertThatThrownBy(() -> ContractSlotId.of(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Exactly one of contractId or hookId must be set");
    }

    @Test
    void ofNullContractIdWithHookId() {
        // Null contractId but valid hookId - should create hook storage
        var hookId = HookId.newBuilder()
                .setHookId(1)
                .setEntityId(HookEntityId.newBuilder()
                        .setAccountId(AccountID.newBuilder().setAccountNum(200)))
                .build();

        var slotId = ContractSlotId.of(null, hookId);

        assertThat(slotId).isNotNull();
        assertThat(slotId.contractId()).isNull();
        assertThat(slotId.hookId()).isEqualTo(hookId);
    }
}
