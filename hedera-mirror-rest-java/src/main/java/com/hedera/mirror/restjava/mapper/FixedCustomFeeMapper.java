// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.mapper;

import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.rest.model.FixedCustomFee;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public interface FixedCustomFeeMapper extends CollectionMapper<FixedFee, FixedCustomFee> {}
