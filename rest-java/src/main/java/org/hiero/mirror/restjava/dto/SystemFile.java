// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import org.hiero.mirror.common.domain.file.FileData;

public record SystemFile<T>(FileData fileData, T data) {}
