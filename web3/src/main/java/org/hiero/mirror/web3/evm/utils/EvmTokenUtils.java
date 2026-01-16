// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.utils;

import static org.hiero.mirror.common.util.DomainUtils.fromEvmAddress;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;

import com.hederahashgraph.api.proto.java.ContractID;
import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hyperledger.besu.datatypes.Address;

@UtilityClass
public class EvmTokenUtils {

    public static Long entityIdNumFromEvmAddress(final Address address) {
        final var id = fromEvmAddress(address.toArrayUnsafe());
        return id != null ? id.getId() : 0;
    }

    public static EntityId entityIdFromEvmAddress(final Address address) {
        return fromEvmAddress(address.toArrayUnsafe());
    }

    public static Address toAddress(final long encodedId) {
        return toAddress(EntityId.of(encodedId));
    }

    public static Address toAddress(final EntityId entityId) {
        final var bytes = Bytes.wrap(toEvmAddress(entityId));
        return Address.wrap(bytes);
    }

    public static Address toAddress(final ContractID contractID) {
        final var bytes = Bytes.wrap(toEvmAddress(contractID));
        return Address.wrap(bytes);
    }
}
