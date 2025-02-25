// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.service;

import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.dto.NftAllowanceRequest;
import java.util.Collection;

public interface NftAllowanceService {

    Collection<NftAllowance> getNftAllowances(NftAllowanceRequest request);
}
