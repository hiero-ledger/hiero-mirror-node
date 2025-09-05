// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.HEX_PREFIX;

import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.rest.model.ContractResponse;
import org.hiero.mirror.rest.model.ContractResult;

public abstract class BaseContractFeature extends AbstractFeature {
    protected DeployedContract deployedParentContract;
    private Long nonceVal = 0L;
    private final List<String> childContracts = new ArrayList<>();

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
        assertThat(address)
                .isNotBlank()
                .isNotEqualTo(HEX_PREFIX)
                .isNotEqualTo("0x0000000000000000000000000000000000000000");
        assertThat(mirrorContract.getTimestamp()).isNotNull();
        assertThat(mirrorContract.getTimestamp().getFrom()).isNotNull();
        assertThat(mirrorContract.getNonce()).isNotNull();
        nonceVal = mirrorContract.getNonce();

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

    protected void verifyContractExecutionResultByIdAndTimestamp(String timestamp) {
        ContractResult contractResult = mirrorClient.getContractResultsByIdAndTimestamp(
                deployedParentContract.contractId().toString(), timestamp);

        assertThat(contractResult).isNotNull();
        assertThat(contractResult.getContractId())
                .isEqualTo(deployedParentContract.contractId().toString());
        assertThat(contractResult.getTimestamp()).isEqualTo(timestamp);
    }

    protected String verifyContractExecutionResultsByTransactionId() {
        ContractResult contractResult = mirrorClient.getContractResultByTransactionId(
                networkTransactionResponse.getTransactionIdStringNoCheckSum());

        verifyContractExecutionResults(contractResult);
        assertThat(contractResult.getBlockHash()).isNotBlank();
        assertThat(contractResult.getBlockNumber()).isPositive();
        assertThat(contractResult.getHash()).isNotBlank();

        return contractResult.getTimestamp();
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
        assertThat(contractResult.getTo())
                .isEqualTo(HEX_PREFIX + deployedParentContract.contractId().toEvmAddress());
    }

    protected void verifyNonceForParentContract() {
        var mirrorContract = verifyContractFromMirror(false);
        assertThat(mirrorContract.getNonce()).isEqualTo(nonceVal);
    }

    protected void verifyNonceForChildContracts() {
        var nonces = childContracts.stream()
                .map(mirrorClient::getContractInfo)
                .map(ContractResponse::getNonce)
                .toList();
        assertThat(nonces).containsOnly(1L);
    }

    protected void addChildContract(String childContract) {
        nonceVal = nonceVal + 1;
        childContracts.add(childContract);
    }
}
