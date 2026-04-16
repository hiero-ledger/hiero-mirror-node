// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestUtils {

    /**
     * Dynamically lookup method references for every getter in object with the given return type
     */
    @SuppressWarnings("unchecked")
    public static <O, R> Collection<Supplier<R>> gettersByType(O object, Class<?> returnType) {
        final var lookup = MethodHandles.lookup();
        final var objectClass = object.getClass();
        final var getters = new ArrayList<Supplier<R>>();

        for (var m : objectClass.getDeclaredMethods()) {
            try {
                if (Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                final var type = MethodType.methodType(returnType, objectClass);
                final var handle = lookup.unreflect(m);
                if (!handle.type().equals(type)) {
                    continue;
                }

                final var functionType = handle.type();
                final var callSite = LambdaMetafactory.metafactory(
                        lookup, "apply", methodType(Function.class), functionType.erase(), handle, functionType);
                final var function = (Function<O, R>) callSite.getTarget().invokeExact();
                getters.add(() -> function.apply(object));
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        return getters;
    }
}
