// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.rest.model.Key.TypeEnum.ED25519;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP_RANGE_NULLABLE;

import java.util.List;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.rest.model.HooksResponse;
import org.hiero.mirror.rest.model.Key;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = MapperConfiguration.class, uses = CommonMapper.class)
public interface HookMapper {
    @Mapping(source = "adminKey", target = "adminKey", qualifiedByName = "mapAdminKey")
    @Mapping(source = "contractId", target = "contractId", qualifiedByName = "formatEntityId")
    @Mapping(source = "createdTimestamp", target = "createdTimestamp", qualifiedByName = "formatTimestamp")
    @Mapping(source = "deleted", target = "deleted")
    @Mapping(source = "extensionPoint", target = "extensionPoint")
    @Mapping(source = "hookId", target = "hookId")
    @Mapping(source = "ownerId", target = "ownerId", qualifiedByName = "formatEntityId")
    @Mapping(source = "type", target = "type")
    @Mapping(source = "timestampRange", target = "timestampRange", qualifiedByName = QUALIFIER_TIMESTAMP_RANGE_NULLABLE)
    org.hiero.mirror.rest.model.Hook mapHook(Hook source);

    @Named("formatEntityId")
    default String formatEntityId(Object entityId) {
        return entityId == null ? null : entityId.toString();
    }

    @Named("formatTimestamp")
    default String formatTimestamp(Long timestamp) {
        return timestamp == null ? null : timestamp.toString();
    }

    @Named("mapAdminKey")
    default Key mapAdminKey(byte[] adminKey) {
        if (adminKey == null) return null;
        Key key = new Key();
        key.setType(ED25519);
        key.setKey(bytesToHex(adminKey));
        return key;
    }

    default String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    default HooksResponse mapToHooksResponse(List<Hook> hooks) {
        HooksResponse response = new HooksResponse();
        response.setHooks(hooks.stream().map(this::mapHook).toList());
        return response;
    }
}
