// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hiero.mirror.restjava.common.Constants.APPLICATION_JSON;
import static org.hiero.mirror.restjava.common.Constants.TIMESTAMP;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.hapi.fees.FeeResult;
import org.hiero.mirror.rest.model.FeeEstimate;
import org.hiero.mirror.rest.model.FeeEstimateMode;
import org.hiero.mirror.rest.model.FeeEstimateNetwork;
import org.hiero.mirror.rest.model.FeeEstimateResponse;
import org.hiero.mirror.rest.model.FeeExtra;
import org.hiero.mirror.rest.model.NetworkExchangeRateSetResponse;
import org.hiero.mirror.rest.model.NetworkFeesResponse;
import org.hiero.mirror.rest.model.NetworkStakeResponse;
import org.hiero.mirror.restjava.common.SupplyType;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.jooq.domain.tables.FileData;
import org.hiero.mirror.restjava.mapper.ExchangeRateMapper;
import org.hiero.mirror.restjava.mapper.FeeScheduleMapper;
import org.hiero.mirror.restjava.mapper.NetworkStakeMapper;
import org.hiero.mirror.restjava.mapper.NetworkSupplyMapper;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.hiero.mirror.restjava.service.FeeEstimationService;
import org.hiero.mirror.restjava.service.FileService;
import org.hiero.mirror.restjava.service.NetworkService;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping(value = "/api/v1/network", produces = APPLICATION_JSON)
@RequiredArgsConstructor
@RestController
final class NetworkController {

    private final ExchangeRateMapper exchangeRateMapper;
    private final FeeEstimationService feeEstimationService;
    private final FeeScheduleMapper feeScheduleMapper;
    private final FileService fileService;
    private final NetworkService networkService;
    private final NetworkStakeMapper networkStakeMapper;
    private final NetworkSupplyMapper networkSupplyMapper;

    @GetMapping("/exchangerate")
    NetworkExchangeRateSetResponse getExchangeRate(
            @RequestParam(required = false) @Size(max = 2) TimestampParameter[] timestamp) {
        final var bound = Bound.of(timestamp, TIMESTAMP, FileData.FILE_DATA.CONSENSUS_TIMESTAMP);
        final var exchangeRateSet = fileService.getExchangeRate(bound);
        return exchangeRateMapper.map(exchangeRateSet);
    }

    @GetMapping("/fees")
    NetworkFeesResponse getFees(
            @RequestParam(required = false) @Size(max = 2) TimestampParameter[] timestamp,
            @RequestParam(required = false, defaultValue = "ASC") Sort.Direction order) {
        final var bound = Bound.of(timestamp, TIMESTAMP, FileData.FILE_DATA.CONSENSUS_TIMESTAMP);
        final var feeSchedule = fileService.getFeeSchedule(bound);
        final var exchangeRate = fileService.getExchangeRate(bound);
        return feeScheduleMapper.map(feeSchedule, exchangeRate, bound, order);
    }

    @PostMapping(
            consumes = {"application/protobuf", "application/x-protobuf"},
            value = "/fees")
    FeeEstimateResponse estimateFees(
            @RequestBody byte[] body,
            @RequestParam(defaultValue = "INTRINSIC", required = false) FeeEstimateMode mode) {
        try {
            final var pbjTxn =
                    com.hedera.hapi.node.base.Transaction.PROTOBUF.parse(body != null ? Bytes.wrap(body) : Bytes.EMPTY);
            return toResponse(feeEstimationService.estimateFees(pbjTxn, mode));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Unable to parse transaction", e);
        }
    }

    @GetMapping("/stake")
    NetworkStakeResponse getNetworkStake() {
        final var networkStake = networkService.getLatestNetworkStake();
        return networkStakeMapper.map(networkStake);
    }

    @GetMapping("/supply")
    ResponseEntity<?> getSupply(
            @RequestParam(required = false) @Size(max = 2) TimestampParameter[] timestamp,
            @RequestParam(name = "q", required = false) SupplyType supplyType) {
        final var bound = Bound.of(timestamp, TIMESTAMP, FileData.FILE_DATA.CONSENSUS_TIMESTAMP);
        final var networkSupply = networkService.getSupply(bound);

        if (supplyType != null) {
            final var valueInTinyCoins =
                    supplyType == SupplyType.TOTALCOINS ? NetworkSupply.TOTAL_SUPPLY : networkSupply.releasedSupply();
            final var formattedValue = networkSupplyMapper.convertToCurrencyFormat(valueInTinyCoins);

            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, UTF_8))
                    .body(formattedValue);
        }

        return ResponseEntity.ok(networkSupplyMapper.map(networkSupply));
    }

    private static FeeEstimateResponse toResponse(FeeResult r) {
        return new FeeEstimateResponse()
                .node(new FeeEstimate().base(r.getNodeBaseFeeTinycents()).extras(toExtras(r.getNodeExtraDetails())))
                .network(new FeeEstimateNetwork()
                        .multiplier(r.getNetworkMultiplier())
                        .subtotal(r.getNetworkTotalTinycents()))
                .service(new FeeEstimate()
                        .base(r.getServiceBaseFeeTinycents())
                        .extras(toExtras(r.getServiceExtraDetails())))
                .total(r.totalTinycents());
    }

    private static List<FeeExtra> toExtras(List<FeeResult.FeeDetail> details) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        final var extras = new ArrayList<FeeExtra>(details.size());
        for (var d : details) {
            extras.add(toExtra(d));
        }
        return extras;
    }

    private static FeeExtra toExtra(FeeResult.FeeDetail d) {
        return new FeeExtra()
                .charged(d.charged())
                .count(d.used())
                .feePerUnit(d.perUnit())
                .included(d.included())
                .name(d.name())
                .subtotal(d.perUnit() * d.charged());
    }
}
