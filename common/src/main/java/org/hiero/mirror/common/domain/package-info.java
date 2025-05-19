// SPDX-License-Identifier: Apache-2.0

@ConverterRegistration(converter = EntityIdConverter.class)
@TypeRegistration(basicClass = Range.class, userType = PostgreSQLGuavaRangeType.class)
package org.hiero.mirror.common.domain;

import com.google.common.collect.Range;
import org.hiero.mirror.common.converter.EntityIdConverter;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import org.hibernate.annotations.ConverterRegistration;
import org.hibernate.annotations.TypeRegistration;
