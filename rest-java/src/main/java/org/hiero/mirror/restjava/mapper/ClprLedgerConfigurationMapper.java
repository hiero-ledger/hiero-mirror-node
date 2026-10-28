// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_HEX;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface ClprLedgerConfigurationMapper
        extends CollectionMapper<ClprLedgerConfiguration, org.hiero.mirror.rest.model.ClprLedgerConfiguration> {

    @Mapping(source = "consensusTimestamp", target = "consensusTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(source = "serviceAddress", target = "serviceAddress", qualifiedByName = QUALIFIER_HEX)
    @Mapping(target = "throttles", source = ".")
    org.hiero.mirror.rest.model.ClprLedgerConfiguration map(ClprLedgerConfiguration source);
}
