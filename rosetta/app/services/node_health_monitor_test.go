// SPDX-License-Identifier: Apache-2.0

package services

import (
	"context"
	"errors"
	"sync/atomic"
	"testing"
	"time"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/stretchr/testify/assert"
)

func newTestNodeHealthMonitor(
	client *hiero.Client,
	cfg config.NodeHealth,
	ping func(nodeID hiero.AccountID) error,
	freeze func(client *hiero.Client) ([]hiero.AccountID, error),
) *nodeHealthMonitor {
	m := &nodeHealthMonitor{
		client:         client,
		config:         cfg,
		freeze:         freeze,
		ping:           ping,
		unhealthyNodes: make(map[string]hiero.AccountID),
	}
	if m.freeze == nil {
		m.freeze = func(_ *hiero.Client) ([]hiero.AccountID, error) {
			return nil, nil
		}
	}
	if m.ping == nil {
		m.ping = func(_ hiero.AccountID) error {
			return nil
		}
	}
	return m
}

func TestFilterHealthy(t *testing.T) {
	node3 := hiero.AccountID{Account: 3}
	node4 := hiero.AccountID{Account: 4}
	node5 := hiero.AccountID{Account: 5}
	allNodes := []hiero.AccountID{node3, node4, node5}

	m := newTestNodeHealthMonitor(nil, config.NodeHealth{}, nil, nil)

	// When no unhealthy nodes
	assert.Equal(t, allNodes, m.FilterHealthy(allNodes))

	// When node 4 is unhealthy
	m.MarkUnhealthy(node4)
	expected := []hiero.AccountID{node3, node5}
	assert.Equal(t, expected, m.FilterHealthy(allNodes))

	// When all nodes are unhealthy
	m.MarkUnhealthy(node3)
	m.MarkUnhealthy(node5)
	assert.Empty(t, m.FilterHealthy(allNodes))

	// When node 3 is marked healthy again
	m.MarkHealthy(node3)
	assert.Equal(t, []hiero.AccountID{node3}, m.FilterHealthy(allNodes))
}

func TestDiscoverUnhealthyNodes(t *testing.T) {
	node3 := hiero.AccountID{Account: 3}
	node4 := hiero.AccountID{Account: 4}
	node5 := hiero.AccountID{Account: 5}

	client, err := hiero.ClientForNetworkV2(map[string]hiero.AccountID{
		"10.0.0.1:50211": node3,
		"10.0.0.2:50211": node4,
		"10.0.0.3:50211": node5,
	})
	assert.NoError(t, err)

	// Freeze returns only node 3 as healthy (nodes 4 and 5 are missing)
	freezeMock := func(_ *hiero.Client) ([]hiero.AccountID, error) {
		return []hiero.AccountID{node3}, nil
	}

	m := newTestNodeHealthMonitor(client, config.NodeHealth{}, nil, freezeMock)
	m.discoverUnhealthyNodes()

	// Nodes 4 and 5 should now be marked unhealthy
	filtered := m.FilterHealthy([]hiero.AccountID{node3, node4, node5})
	assert.Equal(t, []hiero.AccountID{node3}, filtered)

	// Stale nodes in unhealthyNodes not present in network are removed
	staleNode := hiero.AccountID{Account: 999}
	m.MarkUnhealthy(staleNode)
	m.discoverUnhealthyNodes()
	m.mutex.RLock()
	_, exists := m.unhealthyNodes[staleNode.String()]
	m.mutex.RUnlock()
	assert.False(t, exists)
}

func TestDiscoverUnhealthyNodesFreezeError(t *testing.T) {
	node3 := hiero.AccountID{Account: 3}
	client, err := hiero.ClientForNetworkV2(map[string]hiero.AccountID{"10.0.0.1:50211": node3})
	assert.NoError(t, err)

	freezeMock := func(_ *hiero.Client) ([]hiero.AccountID, error) {
		return nil, errors.New("freeze failure")
	}

	m := newTestNodeHealthMonitor(client, config.NodeHealth{}, nil, freezeMock)
	m.discoverUnhealthyNodes()

	assert.Empty(t, m.unhealthyNodes)
}

func TestPingUnhealthyNodes(t *testing.T) {
	node3 := hiero.AccountID{Account: 3}
	node4 := hiero.AccountID{Account: 4}

	// Node 3 recovers (ping succeeds), Node 4 remains broken (ping fails)
	pingMock := func(nodeID hiero.AccountID) error {
		if nodeID == node3 {
			return nil
		}
		return errors.New("connection refused")
	}

	m := newTestNodeHealthMonitor(nil, config.NodeHealth{}, pingMock, nil)
	m.MarkUnhealthy(node3)
	m.MarkUnhealthy(node4)

	m.pingUnhealthyNodes(context.Background())

	// Node 3 should be healthy, Node 4 should still be unhealthy
	m.mutex.RLock()
	_, node3Unhealthy := m.unhealthyNodes[node3.String()]
	_, node4Unhealthy := m.unhealthyNodes[node4.String()]
	m.mutex.RUnlock()

	assert.False(t, node3Unhealthy)
	assert.True(t, node4Unhealthy)
}

func TestProbeSuccess(t *testing.T) {
	node3 := hiero.AccountID{Account: 3}
	node4 := hiero.AccountID{Account: 4}

	pingMock := func(nodeID hiero.AccountID) error {
		if nodeID == node4 {
			return nil
		}
		return errors.New("down")
	}

	cfg := config.NodeHealth{Timeout: 500 * time.Millisecond}
	m := newTestNodeHealthMonitor(nil, cfg, pingMock, nil)
	m.MarkUnhealthy(node3)
	m.MarkUnhealthy(node4)

	recovered, ok := m.Probe(context.Background(), []hiero.AccountID{node3, node4})

	assert.True(t, ok)
	assert.Equal(t, node4, recovered)

	// Node 4 should now be removed from unhealthy
	m.mutex.RLock()
	_, node4Unhealthy := m.unhealthyNodes[node4.String()]
	m.mutex.RUnlock()
	assert.False(t, node4Unhealthy)
}

func TestProbeAllFail(t *testing.T) {
	node3 := hiero.AccountID{Account: 3}
	node4 := hiero.AccountID{Account: 4}

	pingMock := func(_ hiero.AccountID) error {
		return errors.New("down")
	}

	cfg := config.NodeHealth{Timeout: 500 * time.Millisecond}
	m := newTestNodeHealthMonitor(nil, cfg, pingMock, nil)
	m.MarkUnhealthy(node3)
	m.MarkUnhealthy(node4)

	recovered, ok := m.Probe(context.Background(), []hiero.AccountID{node3, node4})

	assert.False(t, ok)
	assert.Equal(t, hiero.AccountID{}, recovered)
}

func TestProbeTimeout(t *testing.T) {
	node3 := hiero.AccountID{Account: 3}

	pingMock := func(_ hiero.AccountID) error {
		time.Sleep(300 * time.Millisecond)
		return nil
	}

	// Timeout shorter than sleep
	cfg := config.NodeHealth{Timeout: 50 * time.Millisecond}
	m := newTestNodeHealthMonitor(nil, cfg, pingMock, nil)
	m.MarkUnhealthy(node3)

	recovered, ok := m.Probe(context.Background(), []hiero.AccountID{node3})

	assert.False(t, ok)
	assert.Equal(t, hiero.AccountID{}, recovered)
}

func TestProbeWithEmptyCandidatesAndClient(t *testing.T) {
	node3 := hiero.AccountID{Account: 3}
	client, err := hiero.ClientForNetworkV2(map[string]hiero.AccountID{"10.0.0.1:50211": node3})
	assert.NoError(t, err)

	pingMock := func(_ hiero.AccountID) error {
		return nil
	}

	cfg := config.NodeHealth{Timeout: 500 * time.Millisecond}
	m := newTestNodeHealthMonitor(client, cfg, pingMock, nil)
	m.MarkUnhealthy(node3)

	recovered, ok := m.Probe(context.Background(), nil)

	assert.True(t, ok)
	assert.Equal(t, node3, recovered)
}

func TestProbeNoCandidatesNoClient(t *testing.T) {
	m := newTestNodeHealthMonitor(nil, config.NodeHealth{Timeout: 500 * time.Millisecond}, nil, nil)
	recovered, ok := m.Probe(context.Background(), nil)
	assert.False(t, ok)
	assert.Equal(t, hiero.AccountID{}, recovered)
}

func TestStartAndStopGracefully(t *testing.T) {
	var pingCalls atomic.Int32
	pingMock := func(_ hiero.AccountID) error {
		pingCalls.Add(1)
		return nil
	}

	cfg := config.NodeHealth{
		Frequency: 50 * time.Millisecond,
		Timeout:   20 * time.Millisecond,
	}

	m := newTestNodeHealthMonitor(nil, cfg, pingMock, nil)
	m.MarkUnhealthy(hiero.AccountID{Account: 3})

	ctx, cancel := context.WithCancel(context.Background())
	m.Start(ctx)

	// Wait for at least one ping call
	assert.Eventually(t, func() bool {
		return pingCalls.Load() > 0
	}, 1*time.Second, 10*time.Millisecond)

	cancel()

	// Wait a moment and confirm it stopped
	snapshot := pingCalls.Load()
	time.Sleep(100 * time.Millisecond)
	assert.Equal(t, snapshot, pingCalls.Load())
}
