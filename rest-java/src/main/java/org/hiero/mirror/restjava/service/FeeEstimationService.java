// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.hiero.hapi.fees.FeeScheduleUtils.lookupExtraFee;
import static org.hiero.hapi.fees.FeeScheduleUtils.lookupServiceFee;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.hapi.fees.FeeResult;
import org.hiero.hapi.fees.FeeScheduleUtils;
import org.hiero.hapi.support.fees.Extra;
import org.hiero.hapi.support.fees.ExtraFeeReference;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.rest.model.FeeEstimate;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.rest.model.FeeEstimateNetwork;
import org.hiero.mirror.rest.model.FeeEstimateResponse;
import org.hiero.mirror.rest.model.FeeExtra;
import org.hiero.mirror.restjava.repository.FileDataRepository;

@Named
@RequiredArgsConstructor
final class FeeEstimationService {

    private final FileDataRepository fileDataRepository;
    private final SystemEntity systemEntity;

    public FeeEstimateResponse estimateFees(Transaction transaction, FeeEstimateMode mode) {
        if (mode == FeeEstimateMode.STATE) {
            throw new IllegalArgumentException("State-based fee estimation is not supported");
        }

        var schedule = loadFeeSchedule();
        var parsed = parseTransaction(transaction);
        var body = parsed.body();
        var sigCount = parsed.signatureMap().getSigPairCount();

        var functionality = getHederaFunctionality(parsed.bodyBytes());
        var serviceFee = lookupServiceFee(schedule, functionality);
        if (serviceFee == null) {
            throw new IllegalArgumentException("Unsupported transaction type: " + body.getDataCase());
        }

        var feeResult = new FeeResult();
        feeResult.setNodeBaseFeeTinycents(schedule.node().baseFee());
        addExtras(feeResult, schedule, schedule.node().extras(), sigCount, body, true);
        feeResult.setNetworkMultiplier(schedule.network().multiplier());
        feeResult.setServiceBaseFeeTinycents(serviceFee.baseFee());
        addExtras(feeResult, schedule, serviceFee.extras(), sigCount, body, false);

        return buildResponse(feeResult);
    }

    private FeeSchedule loadFeeSchedule() {
        var entityId = systemEntity.simpleFeeScheduleFile();
        return fileDataRepository
                .getFileAtTimestamp(entityId.getId(), 0, Long.MAX_VALUE)
                .map(fileData -> {
                    try {
                        var schedule = FeeSchedule.PROTOBUF.parse(Bytes.wrap(fileData.getFileData()));
                        if (!FeeScheduleUtils.isValid(schedule)) {
                            throw new IllegalStateException("Invalid simple fee schedule");
                        }
                        return schedule;
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to parse simple fee schedule", e);
                    }
                })
                .orElseThrow(() -> new EntityNotFoundException("Simple fee schedule (file 113) not found"));
    }

    private void addExtras(
            FeeResult feeResult,
            FeeSchedule schedule,
            List<ExtraFeeReference> refs,
            int sigCount,
            TransactionBody body,
            boolean isNode) {
        for (var ref : refs) {
            var extraDef = lookupExtraFee(schedule, ref.name());
            if (extraDef != null) {
                int used = getExtraUsageCount(ref.name(), sigCount, body);
                if (isNode) {
                    feeResult.addNodeExtraFeeTinycents(ref.name().name(), extraDef.fee(), used, ref.includedCount());
                } else {
                    feeResult.addServiceExtraFeeTinycents(ref.name().name(), extraDef.fee(), used, ref.includedCount());
                }
            }
        }
    }

    private int getExtraUsageCount(Extra extra, int signatureCount, TransactionBody body) {
        return switch (extra) {
            case SIGNATURES -> signatureCount;
            case BYTES -> body.getSerializedSize();
            default -> 0;
        };
    }

    private HederaFunctionality getHederaFunctionality(byte[] bodyBytes) {
        try {
            var pbjBody = com.hedera.hapi.node.transaction.TransactionBody.PROTOBUF.parse(Bytes.wrap(bodyBytes));
            return HapiUtils.functionOf(pbjBody);
        } catch (UnknownHederaFunctionality e) {
            throw new IllegalArgumentException("Unknown transaction type", e);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Failed to determine transaction type", e);
        }
    }

    @SuppressWarnings("deprecation") // Supports legacy Transaction.bodyBytes format
    private ParsedTransaction parseTransaction(Transaction transaction) {
        try {
            byte[] bodyBytes;
            TransactionBody body;
            SignatureMap signatureMap;

            if (!transaction.getBodyBytes().isEmpty()) {
                bodyBytes = transaction.getBodyBytes().toByteArray();
                body = TransactionBody.parseFrom(bodyBytes);
                signatureMap = transaction.hasSigMap() ? transaction.getSigMap() : SignatureMap.getDefaultInstance();
            } else if (!transaction.getSignedTransactionBytes().isEmpty()) {
                var signedTransaction = SignedTransaction.parseFrom(transaction.getSignedTransactionBytes());
                bodyBytes = signedTransaction.getBodyBytes().toByteArray();
                body = TransactionBody.parseFrom(bodyBytes);
                signatureMap = signedTransaction.getSigMap();
            } else {
                throw new IllegalArgumentException("Transaction must contain body bytes or signed transaction bytes");
            }

            return new ParsedTransaction(body, signatureMap, bodyBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Unable to parse transaction: " + e.getMessage(), e);
        }
    }

    private FeeEstimateResponse buildResponse(FeeResult feeResult) {
        var response = new FeeEstimateResponse();

        var nodeFee = new FeeEstimate();
        nodeFee.setBase(feeResult.getNodeBaseFeeTinycents());
        nodeFee.setExtras(feeResult.getNodeExtraDetails().stream()
                .map(FeeEstimationService::toFeeExtra)
                .toList());
        response.setNode(nodeFee);

        var networkFee = new FeeEstimateNetwork();
        networkFee.setMultiplier(feeResult.getNetworkMultiplier());
        networkFee.setSubtotal(feeResult.getNetworkTotalTinycents());
        response.setNetwork(networkFee);

        var serviceFee = new FeeEstimate();
        serviceFee.setBase(feeResult.getServiceBaseFeeTinycents());
        serviceFee.setExtras(feeResult.getServiceExtraDetails().stream()
                .map(FeeEstimationService::toFeeExtra)
                .toList());
        response.setService(serviceFee);

        response.setTotal(feeResult.totalTinycents());
        response.setNotes(List.of());

        return response;
    }

    private static FeeExtra toFeeExtra(FeeResult.FeeDetail detail) {
        var extra = new FeeExtra();
        extra.setName(detail.name());
        extra.setCount((int) detail.used());
        extra.setFeePerUnit(detail.perUnit());
        extra.setIncluded((int) detail.included());
        extra.setCharged((int) detail.charged());
        extra.setSubtotal(detail.perUnit() * detail.charged());
        return extra;
    }

    private record ParsedTransaction(TransactionBody body, SignatureMap signatureMap, byte[] bodyBytes) {}
}
