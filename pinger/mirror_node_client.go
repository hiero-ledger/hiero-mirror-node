// SPDX-License-Identifier: Apache-2.0

package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"strings"
	"time"

	hiero "github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
)

type nodesEnvelope struct {
	Nodes []nodeEntry `json:"nodes"`
	Links struct {
		Next *string `json:"next"`
	} `json:"links"`
}

type nodeEntry struct {
	NodeAccountID    string            `json:"node_account_id"`
	ServiceEndpoints []serviceEndpoint `json:"service_endpoints"`
	GrpcProxyEndpoint *serviceEndpoint `json:"grpc_proxy_endpoint"`
}

type serviceEndpoint struct {
	DomainName  string `json:"domain_name"`
	IPAddressV4 string `json:"ip_address_v4"`
	Port        int    `json:"port"`
}

func buildNetworkFromMirrorNodes(ctx context.Context, mirrorBase string) (map[string]hiero.AccountID, error) {
	base := strings.TrimRight(strings.TrimSpace(mirrorBase), "/")

	var url string
	if strings.HasSuffix(base, "/api/v1") {
		url = base + "/network/nodes"
	} else {
		url = base + "/api/v1/network/nodes"
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}

	httpClient := &http.Client{Timeout: 10 * time.Second}
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("GET %s failed: %w", url, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("GET %s returned %s", url, resp.Status)
	}

	var payload nodesEnvelope
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return nil, fmt.Errorf("decode mirror nodes: %w", err)
	}

	network := make(map[string]hiero.AccountID)

	for _, n := range payload.Nodes {
		if n.NodeAccountID == "" {
			continue
		}
		nodeAccountId, err := hiero.AccountIDFromString(n.NodeAccountID)
		if err != nil {
			continue
		}

		// Use service_endpoints for node gRPC (what the SDK wants)
		for _, ep := range n.ServiceEndpoints {
			host := strings.TrimSpace(ep.DomainName)
			if host == "" {
				host = strings.TrimSpace(ep.IPAddressV4)
			}
			if host == "" || ep.Port == 0 {
				continue
			}

			addr := net.JoinHostPort(host, fmt.Sprintf("%d", ep.Port))
			network[addr] = nodeAccountId
		}
	}

	if len(network) == 0 {
		return nil, fmt.Errorf("no usable service_endpoints found from %s", url)
	}

	return network, nil
}