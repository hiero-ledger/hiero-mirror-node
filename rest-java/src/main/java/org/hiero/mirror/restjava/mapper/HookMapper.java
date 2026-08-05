// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface HookMapper {
    @Mapping(
            target = "createdTimestamp",
            source = "createdTimestamp",
            qualifiedByName = CommonMapper.QUALIFIER_TIMESTAMP)
    org.hiero.mirror.rest.model.Hook map(org.hiero.mirror.common.domain.hook.Hook source);

    List<org.hiero.mirror.rest.model.Hook> map(List<org.hiero.mirror.common.domain.hook.Hook> hooks);
}
