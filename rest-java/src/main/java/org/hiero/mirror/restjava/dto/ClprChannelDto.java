// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import lombok.Builder;

/**
 * Record-based projection of a channel or an open pending channel commitment. Spring Data JPA maps the query column
 * aliases to the record components by position, so the components match the SELECT clause order, which is sorted
 * alphabetically by alias. The SQL does the conversions, so the components only hold simple types: the endpoint
 * manifest is JSON text, the signature scheme and status are text (the status also holds {@code PENDING}), and the
 * timestamp range is split into its lower and upper bounds. A pending commitment only has {@code createdTimestamp},
 * {@code ownershipCommitment}, and {@code status}, and a null {@code channelId}.
 */
@Builder
public record ClprChannelDto(
        Long ackedMessageId,
        String chainId,
        byte[] channelContext,
        byte[] channelId,
        byte[] configProofBytes,
        Long createdTimestamp,
        String endpointManifest,
        byte[] endpointManifestProofBytes,
        Long endpointManifestVersion,
        Long lastConfigTimestamp,
        Long nextMessageId,
        byte[] ownershipCommitment,
        Long peerConfigTimestamp,
        Long peerMaxGasPerMessage,
        Long peerMaxLocalEndpoints,
        Long peerMaxMessagePayloadBytes,
        Long peerMaxMessagesPerBundle,
        Long peerMaxPeerEndpoints,
        Long peerMaxQueueDepth,
        Long peerMaxSyncBytes,
        byte[] publicKey,
        Long receivedMessageId,
        byte[] receivedRunningHash,
        byte[] sentRunningHash,
        byte[] signature,
        String signatureScheme,
        String status,
        Long timestampLower,
        Long timestampUpper,
        byte[] trustAnchor,
        byte[] trustAnchorId,
        Long verifierContractId,
        byte[] verifierFingerprint) {}
