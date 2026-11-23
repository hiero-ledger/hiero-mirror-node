// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.hederahashgraph.api.proto.java.Account;
import com.hederahashgraph.api.proto.java.AccountPendingAirdrop;
import com.hederahashgraph.api.proto.java.Bytecode;
import com.hederahashgraph.api.proto.java.ClprChannel;
import com.hederahashgraph.api.proto.java.ClprConnector;
import com.hederahashgraph.api.proto.java.ClprEndpointManifest;
import com.hederahashgraph.api.proto.java.ClprLedgerConfiguration;
import com.hederahashgraph.api.proto.java.SlotValue;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.Topic;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NullMarked;

/** The state change value fields read under the partial update rules, without protobuf reflection. */
@NullMarked
@UtilityClass
public class StateFields {

    public static final StateField<Account> ACCOUNT_ETHEREUM_NONCE =
            new StateField<>(Account.ETHEREUM_NONCE_FIELD_NUMBER, v -> v.getEthereumNonce() != 0);
    public static final StateField<AccountPendingAirdrop> ACCOUNT_PENDING_AIRDROP_VALUE = new StateField<>(
            AccountPendingAirdrop.PENDING_AIRDROP_VALUE_FIELD_NUMBER, AccountPendingAirdrop::hasPendingAirdropValue);
    public static final StateField<Bytecode> BYTECODE_CODE =
            new StateField<>(Bytecode.CODE_FIELD_NUMBER, v -> !v.getCode().isEmpty());
    public static final StateField<ClprChannel> CLPR_CHANNEL_ACKED_MESSAGE_ID =
            new StateField<>(ClprChannel.ACKED_MESSAGE_ID_FIELD_NUMBER, v -> v.getAckedMessageId() != 0);
    public static final StateField<ClprChannel> CLPR_CHANNEL_CHANNEL_CONTEXT = new StateField<>(
            ClprChannel.CHANNEL_CONTEXT_FIELD_NUMBER,
            v -> !v.getChannelContext().isEmpty());
    public static final StateField<ClprChannel> CLPR_CHANNEL_ENDPOINT_MANIFEST =
            new StateField<>(ClprChannel.ENDPOINT_MANIFEST_FIELD_NUMBER, ClprChannel::hasEndpointManifest);
    public static final StateField<ClprChannel> CLPR_CHANNEL_ENDPOINT_MANIFEST_VERSION = new StateField<>(
            ClprChannel.ENDPOINT_MANIFEST_VERSION_FIELD_NUMBER, v -> v.getEndpointManifestVersion() != 0);
    public static final StateField<ClprChannel> CLPR_CHANNEL_LAST_CONFIG_TIMESTAMP =
            new StateField<>(ClprChannel.LAST_CONFIG_TIMESTAMP_FIELD_NUMBER, ClprChannel::hasLastConfigTimestamp);
    public static final StateField<ClprChannel> CLPR_CHANNEL_NEXT_MESSAGE_ID =
            new StateField<>(ClprChannel.NEXT_MESSAGE_ID_FIELD_NUMBER, v -> v.getNextMessageId() != 0);
    public static final StateField<ClprChannel> CLPR_CHANNEL_OWNERSHIP_COMMITMENT = new StateField<>(
            ClprChannel.OWNERSHIP_COMMITMENT_FIELD_NUMBER,
            v -> !v.getOwnershipCommitment().isEmpty());
    public static final StateField<ClprChannel> CLPR_CHANNEL_PEER_CONFIG_TIMESTAMP =
            new StateField<>(ClprChannel.PEER_CONFIG_TIMESTAMP_FIELD_NUMBER, ClprChannel::hasPeerConfigTimestamp);
    public static final StateField<ClprChannel> CLPR_CHANNEL_PEER_THROTTLES =
            new StateField<>(ClprChannel.PEER_THROTTLES_FIELD_NUMBER, ClprChannel::hasPeerThrottles);
    public static final StateField<ClprChannel> CLPR_CHANNEL_RECEIVED_MESSAGE_ID =
            new StateField<>(ClprChannel.RECEIVED_MESSAGE_ID_FIELD_NUMBER, v -> v.getReceivedMessageId() != 0);
    public static final StateField<ClprChannel> CLPR_CHANNEL_RECEIVED_RUNNING_HASH = new StateField<>(
            ClprChannel.RECEIVED_RUNNING_HASH_FIELD_NUMBER,
            v -> !v.getReceivedRunningHash().isEmpty());
    public static final StateField<ClprChannel> CLPR_CHANNEL_SENT_RUNNING_HASH = new StateField<>(
            ClprChannel.SENT_RUNNING_HASH_FIELD_NUMBER,
            v -> !v.getSentRunningHash().isEmpty());
    public static final StateField<ClprChannel> CLPR_CHANNEL_STATUS =
            new StateField<>(ClprChannel.STATUS_FIELD_NUMBER, v -> v.getStatusValue() != 0);
    public static final StateField<ClprChannel> CLPR_CHANNEL_TRUST_ANCHOR = new StateField<>(
            ClprChannel.TRUST_ANCHOR_FIELD_NUMBER, v -> !v.getTrustAnchor().isEmpty());
    public static final StateField<ClprChannel> CLPR_CHANNEL_TRUST_ANCHOR_ID = new StateField<>(
            ClprChannel.TRUST_ANCHOR_ID_FIELD_NUMBER, v -> !v.getTrustAnchorId().isEmpty());
    public static final StateField<ClprConnector> CLPR_CONNECTOR_ADMIN_KEY =
            new StateField<>(ClprConnector.ADMIN_KEY_FIELD_NUMBER, ClprConnector::hasAdminKey);
    public static final StateField<ClprConnector> CLPR_CONNECTOR_CONNECTOR_CONTRACT =
            new StateField<>(ClprConnector.CONNECTOR_CONTRACT_FIELD_NUMBER, ClprConnector::hasConnectorContract);
    public static final StateField<ClprConnector> CLPR_CONNECTOR_IN_FLIGHT_MESSAGE_COUNT =
            new StateField<>(ClprConnector.IN_FLIGHT_MESSAGE_COUNT_FIELD_NUMBER, v -> v.getInFlightMessageCount() != 0);
    public static final StateField<ClprConnector> CLPR_CONNECTOR_LOCKED_STAKE =
            new StateField<>(ClprConnector.LOCKED_STAKE_FIELD_NUMBER, v -> v.getLockedStake() != 0);
    public static final StateField<ClprConnector> CLPR_CONNECTOR_SLASH_COUNT =
            new StateField<>(ClprConnector.SLASH_COUNT_FIELD_NUMBER, v -> v.getSlashCount() != 0);
    public static final StateField<ClprEndpointManifest> CLPR_ENDPOINT_MANIFEST_ENDPOINTS =
            new StateField<>(ClprEndpointManifest.ENDPOINTS_FIELD_NUMBER, v -> v.getEndpointsCount() > 0);
    public static final StateField<ClprEndpointManifest> CLPR_ENDPOINT_MANIFEST_SERVICE_ADDRESS = new StateField<>(
            ClprEndpointManifest.SERVICE_ADDRESS_FIELD_NUMBER,
            v -> !v.getServiceAddress().isEmpty());
    public static final StateField<ClprEndpointManifest> CLPR_ENDPOINT_MANIFEST_VERSION =
            new StateField<>(ClprEndpointManifest.VERSION_FIELD_NUMBER, v -> v.getVersion() != 0);
    public static final StateField<ClprLedgerConfiguration> CLPR_LEDGER_CONFIGURATION_CHAIN_ID = new StateField<>(
            ClprLedgerConfiguration.CHAIN_ID_FIELD_NUMBER, v -> !v.getChainId().isEmpty());
    public static final StateField<ClprLedgerConfiguration> CLPR_LEDGER_CONFIGURATION_INITIAL_TRUST_ANCHOR =
            new StateField<>(
                    ClprLedgerConfiguration.INITIAL_TRUST_ANCHOR_FIELD_NUMBER,
                    v -> !v.getInitialTrustAnchor().isEmpty());
    public static final StateField<ClprLedgerConfiguration> CLPR_LEDGER_CONFIGURATION_INITIAL_TRUST_ANCHOR_ID =
            new StateField<>(
                    ClprLedgerConfiguration.INITIAL_TRUST_ANCHOR_ID_FIELD_NUMBER,
                    v -> !v.getInitialTrustAnchorId().isEmpty());
    public static final StateField<ClprLedgerConfiguration> CLPR_LEDGER_CONFIGURATION_PROTOCOL_VERSION =
            new StateField<>(ClprLedgerConfiguration.PROTOCOL_VERSION_FIELD_NUMBER, v -> v.getProtocolVersion() != 0);
    public static final StateField<ClprLedgerConfiguration> CLPR_LEDGER_CONFIGURATION_SERVICE_ADDRESS =
            new StateField<>(
                    ClprLedgerConfiguration.SERVICE_ADDRESS_FIELD_NUMBER,
                    v -> !v.getServiceAddress().isEmpty());
    public static final StateField<ClprLedgerConfiguration> CLPR_LEDGER_CONFIGURATION_THROTTLES =
            new StateField<>(ClprLedgerConfiguration.THROTTLES_FIELD_NUMBER, ClprLedgerConfiguration::hasThrottles);
    public static final StateField<SlotValue> SLOT_VALUE_VALUE =
            new StateField<>(SlotValue.VALUE_FIELD_NUMBER, v -> !v.getValue().isEmpty());
    public static final StateField<Token> TOKEN_TOTAL_SUPPLY =
            new StateField<>(Token.TOTAL_SUPPLY_FIELD_NUMBER, v -> v.getTotalSupply() != 0);
    public static final StateField<Topic> TOPIC_RUNNING_HASH = new StateField<>(
            Topic.RUNNING_HASH_FIELD_NUMBER, v -> !v.getRunningHash().isEmpty());
}
