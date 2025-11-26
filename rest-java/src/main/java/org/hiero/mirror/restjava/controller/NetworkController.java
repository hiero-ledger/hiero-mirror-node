// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hiero.mirror.restjava.common.Constants.TIMESTAMP;

import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.NetworkExchangeRateSetResponse;
import org.hiero.mirror.rest.model.NetworkFeesResponse;
import org.hiero.mirror.rest.model.NetworkStakeResponse;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.common.SupplyQuery;
import org.hiero.mirror.restjava.jooq.domain.tables.AccountBalance;
import org.hiero.mirror.restjava.jooq.domain.tables.FileData;
import org.hiero.mirror.restjava.mapper.ExchangeRateMapper;
import org.hiero.mirror.restjava.mapper.FeeScheduleMapper;
import org.hiero.mirror.restjava.mapper.NetworkStakeMapper;
import org.hiero.mirror.restjava.mapper.NetworkSupplyMapper;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.hiero.mirror.restjava.service.FileService;
import org.hiero.mirror.restjava.service.NetworkService;
import org.jooq.TableField;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1/network")
@RequiredArgsConstructor
@RestController
final class NetworkController {

    private final ExchangeRateMapper exchangeRateMapper;
    private final FeeScheduleMapper feeScheduleMapper;
    private final FileService fileService;
    private final NetworkService networkService;
    private final NetworkStakeMapper networkStakeMapper;
    private final NetworkSupplyMapper networkSupplyMapper;

    @GetMapping("/exchangerate")
    NetworkExchangeRateSetResponse getExchangeRate(
            @RequestParam(required = false) @Size(max = 2) TimestampParameter[] timestamp) {
        final var bound = timestampBound(timestamp);
        final var exchangeRateSet = fileService.getExchangeRate(bound);
        return exchangeRateMapper.map(exchangeRateSet);
    }

    @GetMapping("/fees")
    NetworkFeesResponse getFees(
            @RequestParam(required = false) @Size(max = 2) TimestampParameter[] timestamp,
            @RequestParam(required = false, defaultValue = "ASC") Sort.Direction order) {
        final var bound = timestampBound(timestamp);
        final var feeSchedule = fileService.getFeeSchedule(bound);
        final var exchangeRate = fileService.getExchangeRate(bound);
        return feeScheduleMapper.map(feeSchedule, exchangeRate, order);
    }

    @GetMapping("/stake")
    NetworkStakeResponse getNetworkStake() {
        final var networkStake = networkService.getLatestNetworkStake();
        return networkStakeMapper.map(networkStake);
    }

    @GetMapping("/supply")
    ResponseEntity<?> getSupply(
            @RequestParam(required = false) @Size(max = 2) TimestampParameter[] timestamp,
            @RequestParam(required = false) SupplyQuery q) {
        final var bound = supplyTimestampBound(timestamp);
        final var networkSupply = networkService.getSupply(bound);

        if (q != null) {
            final var valueInTinyCoins =
                    q == SupplyQuery.TOTALCOINS ? networkSupply.totalSupply() : networkSupply.releasedSupply();
            final var formattedValue = networkSupplyMapper.convertToCurrencyFormat(valueInTinyCoins);

            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, UTF_8))
                    .body(formattedValue);
        }

        return ResponseEntity.ok(networkSupplyMapper.map(networkSupply));
    }

    private Bound timestampBound(TimestampParameter[] timestamp) {
        return createBound(timestamp, false, FileData.FILE_DATA.CONSENSUS_TIMESTAMP);
    }

    private Bound supplyTimestampBound(TimestampParameter[] timestamp) {
        return createBound(timestamp, true, AccountBalance.ACCOUNT_BALANCE.CONSENSUS_TIMESTAMP);
    }

    private Bound createBound(
            TimestampParameter[] timestamp, boolean useAccountBalance, TableField<?, Long> timestampField) {
        if (timestamp == null || timestamp.length == 0) {
            return Bound.EMPTY;
        }

        for (int i = 0; i < timestamp.length; ++i) {
            final var param = timestamp[i];
            if (param.operator() == RangeOperator.EQ) {
                timestamp[i] = new TimestampParameter(RangeOperator.LTE, param.value());
            }
        }

        return new Bound(timestamp, useAccountBalance, TIMESTAMP, timestampField);
    }
}
