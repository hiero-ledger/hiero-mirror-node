// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.List;
import org.hiero.mirror.rest.model.ClprMessage;
import org.jspecify.annotations.Nullable;

/**
 * The messages of a page. {@code resumeTimestamp} is only set when the service truncated the requested range to the
 * maximum. It's the exclusive boundary to continue from, the truncated range's lower bound when descending and its
 * upper bound when ascending, so a client isn't left thinking a short page is the last one.
 */
public record ClprMessagesResult(
        List<ClprMessage> messages, @Nullable Long resumeTimestamp) {}
