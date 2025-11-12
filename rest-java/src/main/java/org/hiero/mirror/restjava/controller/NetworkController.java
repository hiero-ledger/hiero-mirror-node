// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.hiero.mirror.restjava.common.Constants.TIMESTAMP;
import static org.hiero.mirror.restjava.utils.RangeHelper.timestampBound;

import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.NetworkExchangeRateSetResponse;
import org.hiero.mirror.rest.model.NetworkStakeResponse;
import org.hiero.mirror.restjava.jooq.domain.tables.FileData;
import org.hiero.mirror.restjava.mapper.ExchangeRateMapper;
import org.hiero.mirror.restjava.mapper.NetworkStakeMapper;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.FileService;
import org.hiero.mirror.restjava.service.NetworkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1/network")
@RequiredArgsConstructor
@RestController
final class NetworkController {

    private final ExchangeRateMapper exchangeRateMapper;
    private final FileService fileService;
    private final NetworkService networkService;
    private final NetworkStakeMapper networkStakeMapper;

    @GetMapping("/exchangerate")
    NetworkExchangeRateSetResponse getExchangeRate(
            @RequestParam(required = false) @Size(max = 2) TimestampParameter[] timestamp) {
        final var bound = timestampBound(timestamp, TIMESTAMP, FileData.FILE_DATA.CONSENSUS_TIMESTAMP);
        final var exchangeRateSet = fileService.getExchangeRate(bound);
        return exchangeRateMapper.map(exchangeRateSet);
    }

    @GetMapping("/stake")
    NetworkStakeResponse getNetworkStake() {
        final var networkStake = networkService.getLatestNetworkStake();
        return networkStakeMapper.map(networkStake);
    }
}
