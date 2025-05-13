// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestPropertySource;

@Target({ElementType.TYPE})
@TestPropertySource(properties = "spring.flyway.repeatableSqlMigrationPrefix = DISABLED")
@Retention(RetentionPolicy.RUNTIME)
public @interface DisableRepeatableSqlMigration {}
