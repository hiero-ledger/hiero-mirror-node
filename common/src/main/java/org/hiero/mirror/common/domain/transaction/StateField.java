// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.google.protobuf.Message;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.SingletonUpdateChange;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;

/**
 * A top-level field of a state change value, pairing its field number with a typed check of whether it's set.
 *
 * <p>With state change diffing, a map or singleton update with {@code partial=true} carries only the top-level fields
 * that changed. Each field is then in one of three states:
 * <ul>
 *   <li>set: present on the wire in the value, and its new value replaces the old one whole;</li>
 *   <li>cleared: its {@link #fieldNumber()} is in {@code cleared_fields}, and it resets to the default;</li>
 *   <li>unchanged: anything else. A default-looking value means unchanged, not set to the default.</li>
 * </ul>
 *
 * <p>{@link #isSet()} is the "present on the wire" check, without protobuf reflection:
 * <ul>
 *   <li>message: {@code hasX()}, even when it's empty;</li>
 *   <li>oneof member: selected, even with the default value;</li>
 *   <li>repeated: not empty;</li>
 *   <li>proto3 scalar: not the default, since a change to the default arrives as a clear.</li>
 * </ul>
 * {@code StateFieldsTest} verifies each pairing against the protobuf descriptor.
 */
@NullMarked
public record StateField<T extends Message>(int fieldNumber, Predicate<T> isSet) {

    /**
     * Whether the field of the state change value is present, i.e., the update is full, or the field is set or cleared
     * in a partial update. Any other field in a partial update is unchanged, so its value is absent.
     */
    public boolean isPresent(final MapUpdateChange mapUpdate, final T value) {
        return !mapUpdate.getPartial()
                || isSet.test(value)
                || mapUpdate.getClearedFieldsList().contains(fieldNumber);
    }

    /** Same as {@link #isPresent(MapUpdateChange, Message)}, for a singleton state change. */
    public boolean isPresent(final SingletonUpdateChange singletonUpdate, final T value) {
        return !singletonUpdate.getPartial()
                || isSet.test(value)
                || singletonUpdate.getClearedFieldsList().contains(fieldNumber);
    }
}
