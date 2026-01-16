// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.rest.model.FixedCustomFee;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public interface FixedCustomFeeMapper extends CollectionMapper<FixedFee, FixedCustomFee> {}
