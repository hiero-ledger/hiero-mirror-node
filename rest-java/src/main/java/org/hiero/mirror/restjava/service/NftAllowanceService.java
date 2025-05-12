// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import com.hedera.mirror.common.domain.entity.NftAllowance;
import java.util.Collection;
import org.hiero.mirror.restjava.dto.NftAllowanceRequest;

public interface NftAllowanceService {

    Collection<NftAllowance> getNftAllowances(NftAllowanceRequest request);
}
