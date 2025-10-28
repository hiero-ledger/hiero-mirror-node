// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
final class BlocksResponse {

    private List<Block> blocks = new ArrayList<>();
}
