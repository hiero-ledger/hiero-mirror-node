// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.Collection;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.restjava.dto.NftAllowanceRequest;

public interface NftAllowanceService {

    Collection<NftAllowance> getNftAllowances(NftAllowanceRequest request);
}
