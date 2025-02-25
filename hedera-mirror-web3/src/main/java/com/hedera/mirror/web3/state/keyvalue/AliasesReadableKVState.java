// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.toAccountId;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.state.CommonEntityAccessor;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;

@Named
public class AliasesReadableKVState extends AbstractReadableKVState<ProtoBytes, AccountID> {

    public static final String KEY = "ALIASES";
    private final CommonEntityAccessor commonEntityAccessor;

    protected AliasesReadableKVState(final CommonEntityAccessor commonEntityAccessor) {
        super(KEY);
        this.commonEntityAccessor = commonEntityAccessor;
    }

    @Override
    protected AccountID readFromDataSource(@Nonnull ProtoBytes alias) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        final var entity = commonEntityAccessor.get(alias.value(), timestamp);
        return entity.map(e -> toAccountId(e.getShard(), e.getRealm(), e.getNum()))
                .orElse(null);
    }
}
