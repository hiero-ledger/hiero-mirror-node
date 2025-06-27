// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.asHexAddress;

import java.util.List;
import org.hiero.mirror.rest.model.ContractResponse;
import org.hiero.mirror.rest.model.ContractResult;

public abstract class BaseContractFeature extends AbstractFeature {
    protected DeployedContract deployedParentContract;

    protected ContractResponse verifyContractFromMirror(boolean isDeleted) {
        var mirrorContract =
                mirrorClient.getContractInfo(deployedParentContract.contractId().toString());

        assertThat(mirrorContract).isNotNull();
        assertThat(mirrorContract.getAutoRenewPeriod()).isNotNull();
        assertThat(mirrorContract.getBytecode()).isNotBlank();
        assertThat(mirrorContract.getContractId())
                .isEqualTo(deployedParentContract.contractId().toString());
        assertThat(mirrorContract.getCreatedTimestamp()).isNotBlank();
        assertThat(mirrorContract.getDeleted()).isEqualTo(isDeleted);
        assertThat(mirrorContract.getFileId())
                .isEqualTo(deployedParentContract.fileId().toString());
        String address = mirrorContract.getEvmAddress();
        assertThat(address).isNotBlank().isNotEqualTo("0x").isNotEqualTo("0x0000000000000000000000000000000000000000");
        assertThat(mirrorContract.getTimestamp()).isNotNull();
        assertThat(mirrorContract.getTimestamp().getFrom()).isNotNull();

        if (contractClient
                .getSdkClient()
                .getAcceptanceTestProperties()
                .getFeatureProperties()
                .isSidecars()) {
            assertThat(mirrorContract.getRuntimeBytecode()).isNotNull();
        }

        assertThat(mirrorContract.getBytecode())
                .isEqualTo(deployedParentContract.compiledSolidityArtifact().getBytecode());

        if (isDeleted) {
            assertThat(mirrorContract.getObtainerId())
                    .isEqualTo(contractClient
                            .getSdkClient()
                            .getExpandedOperatorAccountId()
                            .getAccountId()
                            .toString());
        } else {
            assertThat(mirrorContract.getObtainerId()).isNull();
        }

        return mirrorContract;
    }

    protected void verifyContractExecutionResultsById() {
        List<ContractResult> contractResults = mirrorClient
                .getContractResultsById(deployedParentContract.contractId().toString())
                .getResults();

        assertThat(contractResults).isNotEmpty().allSatisfy(this::verifyContractExecutionResults);
    }

    protected void verifyContractExecutionResultsByTransactionId() {
        ContractResult contractResult = mirrorClient.getContractResultByTransactionId(
                networkTransactionResponse.getTransactionIdStringNoCheckSum());

        verifyContractExecutionResults(contractResult);
        assertThat(contractResult.getBlockHash()).isNotBlank();
        assertThat(contractResult.getBlockNumber()).isPositive();
        assertThat(contractResult.getHash()).isNotBlank();
    }

    protected void verifyContractExecutionResults(ContractResult contractResult) {
        assertThat(contractResult.getCallResult()).isNotBlank();
        assertThat(contractResult.getContractId())
                .isEqualTo(deployedParentContract.contractId().toString());

        assertThat(contractResult.getErrorMessage()).isBlank();
        assertThat(contractResult.getFailedInitcode()).isBlank();

        assertThat(contractResult.getGasLimit())
                .isEqualTo(contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas());
        assertThat(contractResult.getGasUsed()).isPositive();
        assertThat(contractResult.getTo()).isEqualTo(asHexAddress(deployedParentContract.contractId()));
    }
}
