// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.dto.ClprConnectorsRequest;
import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.hiero.mirror.restjava.dto.ClprMessagesRequest;
import org.hiero.mirror.restjava.dto.ClprTimestampRequest;

public interface ClprService {

    List<ClprChannel> getChannels(ClprChannelsRequest request);

    List<ClprConnector> getConnectors(ClprConnectorsRequest request);

    List<ClprEndpointManifest> getEndpointManifests(ClprTimestampRequest request);

    List<ClprLedgerConfiguration> getLedgerConfigurations(ClprTimestampRequest request);

    List<ClprMessageDto> getMessages(ClprMessagesRequest request);

    List<ClprChannelPendingCommitment> getPendingChannels(ClprTimestampRequest request);

    List<ClprConnectorPendingCommitment> getPendingConnectors(ClprTimestampRequest request);
}
