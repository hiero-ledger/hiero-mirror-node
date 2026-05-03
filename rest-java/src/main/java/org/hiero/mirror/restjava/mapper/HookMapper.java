// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = MapperConfiguration.class)
public interface HookMapper {

    List<org.hiero.mirror.rest.model.Hook> map(List<org.hiero.mirror.common.domain.hook.Hook> hooks);

    @Mapping(target = "contractId", source = "contractId", qualifiedByName = "mapEntityIdToString")
    @Mapping(target = "ownerId", source = "ownerId", qualifiedByName = "mapOwnerIdToString")
    @Mapping(
            target = "createdTimestamp",
            source = "createdTimestamp",
            qualifiedByName = CommonMapper.QUALIFIER_TIMESTAMP)
    @Mapping(target = "timestampRange", source = "timestampRange")
    @Mapping(target = "extensionPoint", expression = "java(toApiExtensionPoint(source.getExtensionPoint()))")
    @Mapping(target = "type", expression = "java(toApiHookType(source.getType()))")
    @Mapping(target = "deleted", source = "deleted", defaultValue = "false")
    org.hiero.mirror.rest.model.Hook map(org.hiero.mirror.common.domain.hook.Hook source);

    default org.hiero.mirror.rest.model.Hook.ExtensionPointEnum toApiExtensionPoint(
            org.hiero.mirror.common.domain.hook.HookExtensionPoint value) {
        return value == null ? null : org.hiero.mirror.rest.model.Hook.ExtensionPointEnum.fromValue(value.name());
    }

    default org.hiero.mirror.rest.model.Hook.TypeEnum toApiHookType(
            org.hiero.mirror.common.domain.hook.HookType value) {
        return value == null ? null : org.hiero.mirror.rest.model.Hook.TypeEnum.fromValue(value.name());
    }

    @Named("mapEntityIdToString")
    default String mapEntityIdToString(org.hiero.mirror.common.domain.entity.EntityId entityId) {
        return entityId != null ? entityId.toString() : null;
    }

    @Named("mapOwnerIdToString")
    default String mapOwnerIdToString(long ownerId) {
        return org.hiero.mirror.common.domain.entity.EntityId.of(ownerId).toString();
    }
}
