// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.leader;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Only invoke annotated method if currently the leader
 */
@Documented
@Inherited
@Retention(RUNTIME)
@Target({METHOD, TYPE})
public @interface Leader {}
