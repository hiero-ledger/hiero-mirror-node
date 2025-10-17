// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import com.google.protobuf.GeneratedMessage;
import org.hiero.mirror.common.domain.file.FileData;

public record SystemFile<T extends GeneratedMessage>(FileData fileData, T protobuf) {}
