// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.props;

import lombok.Data;

@Data
// POJO content options defined by https://hardhat.org/guides/compile-contracts.html#artifacts
public class CompiledSolidityArtifact {
    private Object[] abi;
    private String bytecode;
}
