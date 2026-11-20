// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.hederahashgraph.api.proto.java.Account;
import com.hederahashgraph.api.proto.java.AccountPendingAirdrop;
import com.hederahashgraph.api.proto.java.Bytecode;
import com.hederahashgraph.api.proto.java.SlotValue;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.Topic;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NullMarked;

/** The state change value fields read under the partial update rules, without protobuf reflection. */
@NullMarked
@UtilityClass
public class StateFields {

    public static final StateField<Account> ACCOUNT_ETHEREUM_NONCE =
            new StateField<>(Account.ETHEREUM_NONCE_FIELD_NUMBER, v -> v.getEthereumNonce() != 0);
    public static final StateField<AccountPendingAirdrop> ACCOUNT_PENDING_AIRDROP_VALUE = new StateField<>(
            AccountPendingAirdrop.PENDING_AIRDROP_VALUE_FIELD_NUMBER, AccountPendingAirdrop::hasPendingAirdropValue);
    public static final StateField<Bytecode> BYTECODE_CODE =
            new StateField<>(Bytecode.CODE_FIELD_NUMBER, v -> !v.getCode().isEmpty());
    public static final StateField<SlotValue> SLOT_VALUE_VALUE =
            new StateField<>(SlotValue.VALUE_FIELD_NUMBER, v -> !v.getValue().isEmpty());
    public static final StateField<Token> TOKEN_TOTAL_SUPPLY =
            new StateField<>(Token.TOTAL_SUPPLY_FIELD_NUMBER, v -> v.getTotalSupply() != 0);
    public static final StateField<Topic> TOPIC_RUNNING_HASH = new StateField<>(
            Topic.RUNNING_HASH_FIELD_NUMBER, v -> !v.getRunningHash().isEmpty());
}
