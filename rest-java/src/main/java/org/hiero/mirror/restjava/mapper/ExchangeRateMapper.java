// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import org.hiero.mirror.rest.model.ExchangeRate;
import org.hiero.mirror.rest.model.NetworkExchangeRateSetResponse;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class, uses = CommonMapper.class)
public interface ExchangeRateMapper {

    @Mapping(source = "protobuf.currentRate", target = "currentRate")
    @Mapping(source = "protobuf.nextRate", target = "nextRate")
    @Mapping(source = "fileData.consensusTimestamp", target = "timestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    NetworkExchangeRateSetResponse map(SystemFile<ExchangeRateSet> source);

    @Mapping(source = "centEquiv", target = "centEquivalent")
    @Mapping(source = "hbarEquiv", target = "hbarEquivalent")
    ExchangeRate map(com.hederahashgraph.api.proto.java.ExchangeRate source);
}
