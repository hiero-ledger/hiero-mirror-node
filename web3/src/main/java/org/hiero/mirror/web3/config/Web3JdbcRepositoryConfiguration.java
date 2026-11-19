// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import org.hiero.mirror.common.repository.MergingJdbcRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

@Configuration(proxyBeanMethods = false)
@EnableJdbcRepositories(
        basePackages = "org.hiero.mirror.web3.repository",
        repositoryBaseClass = MergingJdbcRepository.class)
public class Web3JdbcRepositoryConfiguration {}
