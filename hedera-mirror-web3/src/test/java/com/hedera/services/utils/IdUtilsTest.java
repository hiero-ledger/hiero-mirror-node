// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

class IdUtilsTest {

    @Test
    void testInstantiatingUtilityClassThrowsException() throws Exception {
        Constructor<IdUtils> constructor = IdUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);

        Throwable cause = thrown.getCause();
        assertNotNull(cause, "Cause of exception should not be null");
        assertInstanceOf(UnsupportedOperationException.class, cause, "Expected UnsupportedOperationException");

        assertEquals("Utility class should not be instantiated", cause.getMessage(), "Unexpected exception message");
    }
}
