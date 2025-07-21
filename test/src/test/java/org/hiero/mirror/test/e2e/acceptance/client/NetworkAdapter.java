// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.asHexAddress;

import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.util.Strings;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import jakarta.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.rest.model.ContractCallResponse;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient.NodeNameEnum;
import org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.DeployedContract;
import org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface;
import org.hiero.mirror.test.e2e.acceptance.util.ModelBuilder;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("rawtypes")
@Named
public class NetworkAdapter extends EncoderDecoderFacade {

    @Autowired
    private ContractClient contractClient;

    @Autowired
    private MirrorNodeClient mirrorClient;

    public static final String UINT256 = "(uint256)";

    public static final String BYTES = "(bytes)";

    public static final TupleType BIG_INTEGER_TUPLE = TupleType.parse(UINT256);
    public static final TupleType BYTES_TUPLE = TupleType.parse(BYTES);

    public ContractCallResponse contractsCall(
            final NodeNameEnum node,
            boolean isEstimate,
            final String from,
            final DeployedContract deployedContract,
            final SelectorInterface method,
            final String data,
            final TupleType returnTupleType) {
        if (NodeNameEnum.MIRROR.equals(node)) {
            try {
                var contractCallRequestBody = ModelBuilder.contractCallRequest()
                        .data(data)
                        .estimate(isEstimate)
                        .from(from.isEmpty() ? contractClient.getClientAddress() : from)
                        .to(asHexAddress(deployedContract.contractId()));

                return mirrorClient.contractsCall(contractCallRequestBody);
            } catch (Exception e) {
                ContractCallResponse contractCallResponse = new ContractCallResponse();
                contractCallResponse.setResult(e.getMessage());
                return contractCallResponse;
            }
        } else {
            final var gas = contractClient
                    .getSdkClient()
                    .getAcceptanceTestProperties()
                    .getFeatureProperties()
                    .getMaxContractFunctionGas();

            final var decodedData = Strings.decode(data);
            ContractCallResponse contractCallResponse;
            try {
                final var result = contractClient.executeContractQuery(
                        deployedContract.contractId(), method.getSelector(), gas, decodedData);
                contractCallResponse = convertConsensusResponse(result, returnTupleType);
            } catch (final Exception e) {
                contractCallResponse = new ContractCallResponse();
                if (e instanceof PrecheckStatusException pse) {
                    final var exceptionReason = pse.status.toString();
                    contractCallResponse.setResult(exceptionReason);
                    return contractCallResponse;
                }

                contractCallResponse.setResult(e.getMessage());
            }

            return contractCallResponse;
        }
    }

    @SuppressWarnings("unchecked")
    private ContractCallResponse convertConsensusResponse(
            final ContractFunctionResult result, final TupleType returnTupleType) {
        final var tupleResult = result.getResult(returnTupleType.getCanonicalType());

        final var contractCallResponse = new ContractCallResponse();

        if (isNotEmpty(tupleResult) && tupleResult.get(0) instanceof byte[] bytes) {
            if (bytes.length == 0) {
                contractCallResponse.setResult(StringUtils.EMPTY);
            }
        } else {
            final var encodedResult =
                    Bytes.wrap(returnTupleType.encode(tupleResult).array());
            contractCallResponse.setResult(encodedResult.toString());
        }
        return contractCallResponse;
    }
}
