// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.hiero.mirror.common.util.RuntimeHintsHelper.FIELDS_AND_METHODS;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.NONE;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerAnnotatedPackage;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerJnaAndReflectionTypes;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerPackage;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerReflectionTypes;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerResourcePatterns;

import com.goterl.lazysodium.Sodium;
import com.goterl.lazysodium.SodiumJava;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleGroup;
import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.FromNativeConverter;
import com.sun.jna.IntegerType;
import com.sun.jna.JNIEnv;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.NativeMapped;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.swirlds.config.api.ConfigData;
import lombok.CustomLog;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@CustomLog
@ImportRuntimeHints(RuntimeHintsConfiguration.CustomRuntimeHints.class)
@NullMarked
class RuntimeHintsConfiguration {

    static final class CustomRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            ClassLoader loader = classLoader != null ? classLoader : getClass().getClassLoader();

            registerAnnotatedPackage(hints, loader, "com.hedera.node.config.data", ConfigData.class);

            registerJnaAndReflectionTypes(hints, Sodium.class.getName(), SodiumJava.class.getName());

            registerJnaAndReflectionTypes(hints, FIELDS_AND_METHODS, Native.class.getName());

            registerJnaAndReflectionTypes(
                    hints,
                    NONE,
                    Callback.class.getName(),
                    CallbackReference.class.getName(),
                    FromNativeConverter.class.getName(),
                    IntegerType.class.getName(),
                    JNIEnv.class.getName(),
                    Native.ffi_callback.class.getName(),
                    NativeMapped.class.getName(),
                    Pointer.class.getName(),
                    PointerType.class.getName(),
                    Structure.class.getName(),
                    Structure.ByValue.class.getName(),
                    WString.class.getName(),
                    "com.sun.jna.Structure$FFIType$FFITypes",
                    "com.sun.jna.CallbackReference$AttachOptions");

            registerReflectionTypes(
                    hints,
                    IntByReference.class.getName(),
                    NativeLibrary.class.getName(),
                    NativeLong.class.getName(),
                    "com.sun.jna.Structure$FFIType",
                    "com.sun.jna.Structure$FFIType$size_t",
                    "sun.security.provider.NativePRNG");

            registerPackage(hints, loader, ThrottleGroup.class.getPackageName());

            registerResourcePatterns(
                    hints,
                    "com/hedera/nativelib/hints/**",
                    "com/hedera/nativelib/wraps/**",
                    "genesis/**",
                    "semantic-version.properties");
        }
    }
}
