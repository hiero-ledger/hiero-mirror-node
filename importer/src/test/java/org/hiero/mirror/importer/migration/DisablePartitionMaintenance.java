// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestPropertySource;

/**
 * Disable migration tests whose target is before the migration that creates account_balance and token_balance
 * partitions.
 */
@Target({ElementType.TYPE})
@TestPropertySource(properties = "hiero.mirror.importer.db.partition.enabled=false")
@Retention(RetentionPolicy.RUNTIME)
public @interface DisablePartitionMaintenance {}
