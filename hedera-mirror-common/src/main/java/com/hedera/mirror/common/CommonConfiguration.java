// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationPropertiesScan(basePackages = "com.hedera.mirror")
@EntityScan("com.hedera.mirror.common.domain")
public class CommonConfiguration {}
