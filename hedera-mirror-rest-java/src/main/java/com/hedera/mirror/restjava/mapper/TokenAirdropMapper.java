// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.mapper;

import com.hedera.mirror.common.domain.token.TokenAirdrop;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = MapperConfiguration.class)
public interface TokenAirdropMapper extends CollectionMapper<TokenAirdrop, com.hedera.mirror.rest.model.TokenAirdrop> {

    @Mapping(source = "receiverAccountId", target = "receiverId")
    @Mapping(source = "senderAccountId", target = "senderId")
    @Mapping(source = "serialNumber", target = "serialNumber", qualifiedByName = "mapToNullIfZero")
    @Mapping(source = "timestampRange", target = "timestamp")
    com.hedera.mirror.rest.model.TokenAirdrop map(TokenAirdrop source);

    @Named("mapToNullIfZero")
    default Long mapToNullIfZero(long serialNumber) {
        if (serialNumber == 0L) {
            return null;
        }
        return serialNumber;
    }
}
