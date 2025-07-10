// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql;

import org.hiero.mirror.common.config.CommonIntegrationTest;
import org.hiero.mirror.graphql.config.GraphqlTestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {GraphqlApplication.class, GraphqlTestConfiguration.class})
public abstract class GraphqlIntegrationTest extends CommonIntegrationTest {}
