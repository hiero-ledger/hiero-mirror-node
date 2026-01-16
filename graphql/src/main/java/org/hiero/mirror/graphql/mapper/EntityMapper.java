// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.mapper;

import static org.mapstruct.MappingInheritanceStrategy.AUTO_INHERIT_FROM_CONFIG;

import org.hiero.mirror.graphql.viewmodel.Entity;
import org.mapstruct.MapperConfig;
import org.mapstruct.Mapping;

@MapperConfig(mappingInheritanceStrategy = AUTO_INHERIT_FROM_CONFIG, uses = CommonMapper.class)
public interface EntityMapper<T extends Entity> {

    @Mapping(
            expression = "java(org.hiero.mirror.common.util.DomainUtils.bytesToHex(source.getAlias()))",
            target = "alias")
    @Mapping(source = "ethereumNonce", target = "nonce")
    @Mapping(source = "id", target = "entityId")
    @Mapping(source = "timestampRange", target = "timestamp")
    T map(org.hiero.mirror.common.domain.entity.Entity source);
}
