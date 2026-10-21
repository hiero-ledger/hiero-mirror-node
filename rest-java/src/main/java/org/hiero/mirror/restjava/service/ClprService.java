// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;

public interface ClprService {

    List<ClprChannel> getChannels(ClprChannelsRequest request);
}
