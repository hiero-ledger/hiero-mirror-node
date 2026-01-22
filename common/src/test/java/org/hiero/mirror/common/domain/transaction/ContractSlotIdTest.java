// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HookEntityId;
import com.hederahashgraph.api.proto.java.HookId;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@SpringBootTest(classes = CommonProperties.class)
class ContractSlotIdTest {

    private static final long HOOK_SYSTEM_CONTRACT_NUM = 365L;

    private final CommonProperties commonProperties;

    private ContractID contractId(long num) {
        return ContractID.newBuilder()
                .setShardNum(commonProperties.getShard())
                .setRealmNum(commonProperties.getRealm())
                .setContractNum(num)
                .build();
    }

    @Test
    void ofRegularContractWithoutHook() {
        // Regular contract (not 365) without executed hook
        var contractId = contractId(100);

        var slotId = ContractSlotId.of(contractId, null);

        assertThat(slotId).isNotNull();
        assertThat(slotId.getContractId()).isEqualTo(contractId);
        assertThat(slotId.getHookId()).isNull();
    }

    @Test
    void ofRegularContractWithHook() {
        // Regular contract (not 365) WITH executed hook - hook should be IGNORED
        var contractId = contractId(100);
        var hookId = HookId.newBuilder()
                .setHookId(1)
                .setEntityId(HookEntityId.newBuilder()
                        .setAccountId(AccountID.newBuilder().setAccountNum(200)))
                .build();

        var slotId = ContractSlotId.of(contractId, hookId);

        // Hook should be ignored for regular contracts
        assertThat(slotId).isNotNull();
        assertThat(slotId.getContractId()).isEqualTo(contractId);
        assertThat(slotId.getHookId()).isNull();
    }

    @Test
    void ofHookSystemContractWithHook() {
        // Hook system contract (365) with executed hook
        var contractId = contractId(HOOK_SYSTEM_CONTRACT_NUM);
        var hookId = HookId.newBuilder()
                .setHookId(1)
                .setEntityId(HookEntityId.newBuilder()
                        .setAccountId(AccountID.newBuilder().setAccountNum(200)))
                .build();

        var slotId = ContractSlotId.of(contractId, hookId);

        // Should be hook storage
        assertThat(slotId).isNotNull();
        assertThat(slotId.getContractId()).isNull();
        assertThat(slotId.getHookId()).isEqualTo(hookId);
    }

    @Test
    void ofHookSystemContractWithoutHook_ReturnsNull() {
        // Hook system contract (365) WITHOUT executed hook - INVALID STATE
        var contractId = contractId(HOOK_SYSTEM_CONTRACT_NUM);

        var slotId = ContractSlotId.of(contractId, null);

        // Should return null for invalid state
        assertThat(slotId).isNull();
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
        assertThat(slotId.getContractId()).isNull();
        assertThat(slotId.getHookId()).isEqualTo(hookId);
    }
}
