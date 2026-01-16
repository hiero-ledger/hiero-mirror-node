// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.mapstruct.MappingInheritanceStrategy.AUTO_INHERIT_FROM_CONFIG;

import org.mapstruct.MapperConfig;

@MapperConfig(mappingInheritanceStrategy = AUTO_INHERIT_FROM_CONFIG, uses = CommonMapper.class)
public interface MapperConfiguration {}
