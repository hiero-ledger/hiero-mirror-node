// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.graphql.mapper;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.graphql.viewmodel.Account;
import org.mapstruct.Mapper;

@Mapper(config = EntityMapper.class)
public interface AccountMapper {
    Account map(Entity source);
}
