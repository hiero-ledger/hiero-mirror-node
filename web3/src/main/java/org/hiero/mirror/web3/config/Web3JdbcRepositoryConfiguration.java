// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

@Configuration(proxyBeanMethods = false)
@EnableJdbcRepositories("org.hiero.mirror.web3.repository")
public class Web3JdbcRepositoryConfiguration {}
