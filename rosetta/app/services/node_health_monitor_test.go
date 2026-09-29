// SPDX-License-Identifier: Apache-2.0

package services

import (
	"context"
	"errors"
	"fmt"
	"net"
	"slices"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	"github.com/hiero-ledger/hiero-sdk-go/v2/proto/services"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	pkgErrors "github.com/pkg/errors"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

const testMaxConcurrency = 20

var (
	errNodeDown = status.Error(codes.Unavailable, "node is down")
	node3       = hiero.AccountID{Account: 3}
	node4       = hiero.AccountID{Account: 4}
	node5       = hiero.AccountID{Account: 5}
)

// concurrencyTracker records the most pings in flight at once.
type concurrencyTracker struct {
	inFlight atomic.Int32
	max      atomic.Int32
}

func (c *concurrencyTracker) track(ping func(node hiero.AccountID) error) func(node hiero.AccountID) error {
	return func(node hiero.AccountID) error {
		inFlight := c.inFlight.Add(1)
		defer c.inFlight.Add(-1)
		for observed := c.max.Load(); inFlight > observed; observed = c.max.Load() {
			if c.max.CompareAndSwap(observed, inFlight) {
				break
			}
		}
		return ping(node)
	}
}

// mockConsensusNode is an in-process consensus node. It answers account info queries and crypto transfers with the
// configured precheck status, or fails them with gRPC status UNAVAILABLE while it's down.
type mockConsensusNode struct {
	services.UnimplementedCryptoServiceServer
	address  string
	down     atomic.Bool
	precheck atomic.Int32
	requests atomic.Int32
}

func newMockConsensusNode(t *testing.T) *mockConsensusNode {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	require.NoError(t, err)

	node := &mockConsensusNode{address: listener.Addr().String()}
	server := grpc.NewServer()
	services.RegisterCryptoServiceServer(server, node)
	go func() { _ = server.Serve(listener) }()
	t.Cleanup(server.Stop)
	return node
}

func (n *mockConsensusNode) CryptoTransfer(
	context.Context,
	*services.Transaction,
) (*services.TransactionResponse, error) {
	if err := n.receive(); err != nil {
		return nil, err
	}

	return &services.TransactionResponse{NodeTransactionPrecheckCode: services.ResponseCodeEnum(n.precheck.Load())}, nil
}

func (n *mockConsensusNode) GetAccountInfo(context.Context, *services.Query) (*services.Response, error) {
	if err := n.receive(); err != nil {
		return nil, err
	}

	header := &services.ResponseHeader{
		NodeTransactionPrecheckCode: services.ResponseCodeEnum(n.precheck.Load()),
		ResponseType:                services.ResponseType_COST_ANSWER,
	}
	return &services.Response{
		Response: &services.Response_CryptoGetInfo{CryptoGetInfo: &services.CryptoGetInfoResponse{Header: header}},
	}, nil
}

func (n *mockConsensusNode) receive() error {
	n.requests.Add(1)
	if n.down.Load() {
		return errNodeDown
	}
	return nil
}

func newTestClient(t *testing.T, network map[string]hiero.AccountID) *hiero.Client {
	client, err := hiero.ClientForNetworkV2(network)
	require.NoError(t, err)
	client.SetMaxAttempts(1)
	t.Cleanup(func() { _ = client.Close() })
	return client
}

// newTestNodeHealthMonitor creates a monitor for nodes 0.0.3, 0.0.4 and 0.0.5 that never contacts them.
func newTestNodeHealthMonitor(
	t *testing.T,
	cfg config.NodeHealth,
	ping func(node hiero.AccountID) error,
) *nodeHealthMonitor {
	client := newTestClient(t, map[string]hiero.AccountID{
		"10.0.0.1:50211": node3,
		"10.0.0.2:50211": node4,
		"10.0.0.3:50211": node5,
	})
	return newNodeHealthMonitor(client, cfg, ping)
}

func pingNodes(healthy ...hiero.AccountID) func(node hiero.AccountID) error {
	return func(node hiero.AccountID) error {
		if slices.Contains(healthy, node) {
			return nil
		}
		return errNodeDown
	}
}

func isUnhealthy(m *nodeHealthMonitor, node hiero.AccountID) bool {
	return len(m.FilterHealthy([]hiero.AccountID{node})) == 0
}

func TestFilterHealthy(t *testing.T) {
	allNodes := []hiero.AccountID{node3, node4, node5}
	m := newTestNodeHealthMonitor(t, config.NodeHealth{MaxConcurrency: testMaxConcurrency}, pingNodes())

	// When no unhealthy nodes
	assert.Equal(t, allNodes, m.FilterHealthy(allNodes))

	// When node 4 is unhealthy
	m.MarkUnhealthy(node4)
	assert.Equal(t, []hiero.AccountID{node3, node5}, m.FilterHealthy(allNodes))

	// When all nodes are unhealthy
	m.MarkUnhealthy(node3)
	m.MarkUnhealthy(node5)
	assert.Empty(t, m.FilterHealthy(allNodes))

	// When node 3 is marked healthy again
	m.markHealthy(node3, time.Now())
	assert.Equal(t, []hiero.AccountID{node3}, m.FilterHealthy(allNodes))
}

func TestMarkHealthyIgnoresPingStartedBeforeLatestFailure(t *testing.T) {
	m := newTestNodeHealthMonitor(t, config.NodeHealth{MaxConcurrency: testMaxConcurrency}, pingNodes())
	pingStartedAt := time.Now().Add(-time.Second)
	m.MarkUnhealthy(node3)

	// A ping started before the failure doesn't vouch for the node
	m.markHealthy(node3, pingStartedAt)
	assert.True(t, isUnhealthy(m, node3))

	m.markHealthy(node3, time.Now())
	assert.False(t, isUnhealthy(m, node3))
}

func TestMarkUnhealthyRecordsLatestFailure(t *testing.T) {
	m := newTestNodeHealthMonitor(t, config.NodeHealth{MaxConcurrency: testMaxConcurrency}, pingNodes())
	m.unhealthyNodes[node3.String()] = unhealthyNode{failedAt: time.Now().Add(-time.Minute), id: node3}
	pingStartedAt := time.Now().Add(-time.Second)

	// The node fails again while the ping is in flight
	m.MarkUnhealthy(node3)
	m.markHealthy(node3, pingStartedAt)

	assert.True(t, isUnhealthy(m, node3))
}

func TestPingAllNodes(t *testing.T) {
	// given nodes 3 and 4 marked unhealthy, and nodes 5 and 6 healthy
	node6 := hiero.AccountID{Account: 6}
	client := newTestClient(t, map[string]hiero.AccountID{
		"10.0.0.1:50211": node3,
		"10.0.0.2:50211": node4,
		"10.0.0.3:50211": node5,
		"10.0.0.4:50211": node6,
	})
	var pings atomic.Int32
	ping := func(node hiero.AccountID) error {
		pings.Add(1)
		return pingNodes(node3, node5)(node)
	}
	m := newNodeHealthMonitor(client, config.NodeHealth{MaxConcurrency: testMaxConcurrency}, ping)
	m.MarkUnhealthy(node3)
	m.MarkUnhealthy(node4)

	// when
	m.pingAllNodes(context.Background())

	// then every node is pinged: node 3 recovered, node 4 is still down, node 5 is still up and node 6 went down
	assert.Equal(t, int32(4), pings.Load())
	assert.False(t, isUnhealthy(m, node3))
	assert.True(t, isUnhealthy(m, node4))
	assert.False(t, isUnhealthy(m, node5))
	assert.True(t, isUnhealthy(m, node6))
}

func TestPingAllNodesForgetsNodesNotInNetwork(t *testing.T) {
	var pings atomic.Int32
	ping := func(hiero.AccountID) error {
		pings.Add(1)
		return errNodeDown
	}
	m := newTestNodeHealthMonitor(t, config.NodeHealth{MaxConcurrency: testMaxConcurrency}, ping)
	staleNode := hiero.AccountID{Account: 999}
	m.MarkUnhealthy(staleNode)

	m.pingAllNodes(context.Background())

	// Only the 3 nodes in the network are pinged
	assert.Equal(t, int32(3), pings.Load())
	assert.NotContains(t, m.unhealthyNodes, staleNode.String())
}

func TestPingAllNodesLimitsConcurrency(t *testing.T) {
	// given 6 nodes and 2 ping slots
	network := make(map[string]hiero.AccountID)
	for i := range 6 {
		network[fmt.Sprintf("10.0.0.%d:50211", i+1)] = hiero.AccountID{Account: uint64(3 + i)}
	}
	var pings atomic.Int32
	var tracker concurrencyTracker
	ping := tracker.track(func(hiero.AccountID) error {
		pings.Add(1)
		time.Sleep(20 * time.Millisecond)
		return errNodeDown
	})
	m := newNodeHealthMonitor(newTestClient(t, network), config.NodeHealth{MaxConcurrency: 2}, ping)

	// when
	m.pingAllNodes(context.Background())

	// then every node is pinged, at most 2 at a time
	assert.Equal(t, int32(len(network)), pings.Load())
	assert.Equal(t, int32(2), tracker.max.Load())
}

func TestIsReachable(t *testing.T) {
	tests := []struct {
		name     string
		err      error
		expected bool
	}{
		{name: "answered", expected: true},
		{
			name:     "invalid account",
			err:      hiero.ErrHederaPreCheckStatus{Status: hiero.StatusInvalidAccountID},
			expected: true,
		},
		{name: "busy", err: hiero.ErrHederaPreCheckStatus{Status: hiero.StatusBusy}},
		{
			name: "wrapped platform not active",
			err:  pkgErrors.Wrapf(hiero.ErrHederaPreCheckStatus{Status: hiero.StatusPlatformNotActive}, "retry 1/1"),
		},
		{name: "unavailable", err: errNodeDown},
		{name: "in sdk backoff", err: errors.New("unknown error occurred after max attempts")},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, isReachable(tt.err))
		})
	}
}

func TestProbeSuccess(t *testing.T) {
	cfg := config.NodeHealth{MaxConcurrency: testMaxConcurrency, Timeout: 500 * time.Millisecond}
	m := newTestNodeHealthMonitor(t, cfg, pingNodes(node4))
	m.MarkUnhealthy(node3)
	m.MarkUnhealthy(node4)

	recovered, ok := m.Probe(context.Background(), []hiero.AccountID{node3, node4})

	assert.True(t, ok)
	assert.Equal(t, node4, recovered)
	assert.True(t, isUnhealthy(m, node3))
	assert.False(t, isUnhealthy(m, node4))
}

func TestProbeReleasesEveryNodeThatAnswers(t *testing.T) {
	// given pings that answer only once all of them are in flight
	var started atomic.Int32
	release := make(chan struct{})
	ping := func(node hiero.AccountID) error {
		started.Add(1)
		<-release
		return pingNodes(node3, node4)(node)
	}
	cfg := config.NodeHealth{MaxConcurrency: testMaxConcurrency, Timeout: 5 * time.Second}
	m := newTestNodeHealthMonitor(t, cfg, ping)
	candidates := []hiero.AccountID{node3, node4, node5}
	for _, node := range candidates {
		m.MarkUnhealthy(node)
	}

	// when
	results := make(chan probeResult, 1)
	go func() {
		recovered, ok := m.Probe(context.Background(), candidates)
		results <- probeResult{node: recovered, ok: ok}
	}()
	require.Eventually(t, func() bool { return started.Load() == 3 }, time.Second, time.Millisecond)
	close(release)
	result := <-results

	// then both nodes that answered are healthy, not just the one returned
	assert.True(t, result.ok)
	assert.Contains(t, []hiero.AccountID{node3, node4}, result.node)
	assert.Eventually(t, func() bool {
		return !isUnhealthy(m, node3) && !isUnhealthy(m, node4)
	}, time.Second, 10*time.Millisecond)
	assert.True(t, isUnhealthy(m, node5))
}

func TestProbeStopsStartingPingsOnceOneAnswers(t *testing.T) {
	// given one ping slot, and node 3 answering while the other nodes don't until released
	var pinged sync.Map
	release := make(chan struct{})
	ping := func(node hiero.AccountID) error {
		pinged.Store(node, true)
		if node == node3 {
			return nil
		}

		<-release
		return errNodeDown
	}
	m := newTestNodeHealthMonitor(t, config.NodeHealth{MaxConcurrency: 1, Timeout: 5 * time.Second}, ping)
	candidates := []hiero.AccountID{node3, node4, node5}
	for _, node := range candidates {
		m.MarkUnhealthy(node)
	}

	// when
	recovered, ok := m.Probe(context.Background(), candidates)
	close(release)

	// then node 5 is never pinged, although node 4 may have started before the probe returned
	assert.True(t, ok)
	assert.Equal(t, node3, recovered)
	assert.Never(t, func() bool {
		_, node5Pinged := pinged.Load(node5)
		return node5Pinged
	}, 100*time.Millisecond, 10*time.Millisecond)
}

func TestProbeWaitsForPingSlotsWithinTimeout(t *testing.T) {
	// given a round of pings holding the only ping slot
	var pings atomic.Int32
	release := make(chan struct{})
	ping := func(hiero.AccountID) error {
		pings.Add(1)
		<-release
		return errNodeDown
	}
	m := newTestNodeHealthMonitor(t, config.NodeHealth{MaxConcurrency: 1, Timeout: 50 * time.Millisecond}, ping)
	pinged := make(chan struct{})
	go func() {
		m.pingAllNodes(context.Background())
		close(pinged)
	}()
	require.Eventually(t, func() bool { return pings.Load() == 1 }, time.Second, time.Millisecond)
	m.MarkUnhealthy(node4)

	// when
	recovered, ok := m.Probe(context.Background(), []hiero.AccountID{node4})

	// then the probe gives up at its timeout without pinging
	assert.False(t, ok)
	assert.Equal(t, hiero.AccountID{}, recovered)
	assert.Equal(t, int32(1), pings.Load())
	close(release)
	select {
	case <-pinged:
	case <-time.After(time.Second):
		t.Fatal("round of pings didn't finish")
	}
}

func TestProbeAllFail(t *testing.T) {
	cfg := config.NodeHealth{MaxConcurrency: testMaxConcurrency, Timeout: 500 * time.Millisecond}
	m := newTestNodeHealthMonitor(t, cfg, pingNodes())
	m.MarkUnhealthy(node3)
	m.MarkUnhealthy(node4)

	recovered, ok := m.Probe(context.Background(), []hiero.AccountID{node3, node4})

	assert.False(t, ok)
	assert.Equal(t, hiero.AccountID{}, recovered)
}

func TestProbeTimeout(t *testing.T) {
	release := make(chan struct{})
	t.Cleanup(func() { close(release) })
	ping := func(hiero.AccountID) error {
		<-release
		return nil
	}
	cfg := config.NodeHealth{MaxConcurrency: testMaxConcurrency, Timeout: 50 * time.Millisecond}
	m := newTestNodeHealthMonitor(t, cfg, ping)
	m.MarkUnhealthy(node3)

	recovered, ok := m.Probe(context.Background(), []hiero.AccountID{node3})

	assert.False(t, ok)
	assert.Equal(t, hiero.AccountID{}, recovered)
}

func TestProbeReturnsWhenContextDone(t *testing.T) {
	release := make(chan struct{})
	t.Cleanup(func() { close(release) })
	ping := func(hiero.AccountID) error {
		<-release
		return nil
	}
	m := newTestNodeHealthMonitor(t, config.NodeHealth{MaxConcurrency: testMaxConcurrency, Timeout: time.Minute}, ping)
	m.MarkUnhealthy(node3)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	recovered, ok := m.Probe(ctx, []hiero.AccountID{node3})

	assert.False(t, ok)
	assert.Equal(t, hiero.AccountID{}, recovered)
}

func TestProbeSharesOneRoundOfPings(t *testing.T) {
	const callers = 5
	var pings atomic.Int32
	release := make(chan struct{})
	ping := func(node hiero.AccountID) error {
		pings.Add(1)
		<-release
		return pingNodes(node4)(node)
	}
	cfg := config.NodeHealth{MaxConcurrency: testMaxConcurrency, Timeout: 5 * time.Second}
	m := newTestNodeHealthMonitor(t, cfg, ping)
	candidates := []hiero.AccountID{node3, node4}
	m.MarkUnhealthy(node3)
	m.MarkUnhealthy(node4)

	results := make(chan hiero.AccountID, callers)
	var ready, done sync.WaitGroup
	for range callers {
		ready.Add(1)
		done.Go(func() {
			ready.Done()
			if recovered, ok := m.Probe(context.Background(), candidates); ok {
				results <- recovered
			}
		})
	}

	// Release the pings only once every caller is waiting on the round in flight
	ready.Wait()
	assert.Eventually(t, func() bool { return pings.Load() == int32(len(candidates)) }, time.Second, time.Millisecond)
	time.Sleep(50 * time.Millisecond)
	close(release)
	done.Wait()
	close(results)

	assert.Equal(t, int32(len(candidates)), pings.Load())
	var recovered []hiero.AccountID
	for node := range results {
		recovered = append(recovered, node)
	}
	assert.Equal(t, slices.Repeat([]hiero.AccountID{node4}, callers), recovered)
}

func TestProbeWithEmptyCandidates(t *testing.T) {
	cfg := config.NodeHealth{MaxConcurrency: testMaxConcurrency, Timeout: 500 * time.Millisecond}
	m := newTestNodeHealthMonitor(t, cfg, pingNodes(node3))
	m.MarkUnhealthy(node3)

	recovered, ok := m.Probe(context.Background(), nil)
	assert.False(t, ok)
	assert.Equal(t, hiero.AccountID{}, recovered)

	recovered, ok = m.Probe(context.Background(), []hiero.AccountID{})
	assert.False(t, ok)
	assert.Equal(t, hiero.AccountID{}, recovered)
}

func TestProbeCoolsDownAfterFindingNoNode(t *testing.T) {
	// given a probe that found no node
	var pings atomic.Int32
	ping := func(hiero.AccountID) error {
		pings.Add(1)
		return errNodeDown
	}
	cfg := config.NodeHealth{
		MaxConcurrency: testMaxConcurrency,
		ProbeCooldown:  time.Minute,
		Timeout:        time.Second,
	}
	m := newTestNodeHealthMonitor(t, cfg, ping)
	candidates := []hiero.AccountID{node3, node4}
	for _, node := range candidates {
		m.MarkUnhealthy(node)
	}
	_, ok := m.Probe(context.Background(), candidates)
	require.False(t, ok)
	require.Equal(t, int32(2), pings.Load())

	// when probing again within the cool-down
	recovered, ok := m.Probe(context.Background(), candidates)

	// then it fails fast without pinging
	assert.False(t, ok)
	assert.Equal(t, hiero.AccountID{}, recovered)
	assert.Equal(t, int32(2), pings.Load())
}

func TestProbeAfterCooldown(t *testing.T) {
	// given a probe that found no node, after which node 4 recovers
	var node4Up atomic.Bool
	ping := func(node hiero.AccountID) error {
		if node == node4 && node4Up.Load() {
			return nil
		}
		return errNodeDown
	}
	cfg := config.NodeHealth{
		MaxConcurrency: testMaxConcurrency,
		ProbeCooldown:  50 * time.Millisecond,
		Timeout:        time.Second,
	}
	m := newTestNodeHealthMonitor(t, cfg, ping)
	candidates := []hiero.AccountID{node3, node4}
	for _, node := range candidates {
		m.MarkUnhealthy(node)
	}
	_, ok := m.Probe(context.Background(), candidates)
	require.False(t, ok)
	node4Up.Store(true)

	// then once the cool-down elapses, the probe pings again and finds node 4
	assert.Eventually(t, func() bool {
		recovered, ok := m.Probe(context.Background(), candidates)
		return ok && recovered == node4
	}, time.Second, 10*time.Millisecond)
}

func TestProbeSuccessDoesNotCoolDown(t *testing.T) {
	// given a probe that found node 3
	var pings atomic.Int32
	ping := func(node hiero.AccountID) error {
		pings.Add(1)
		return pingNodes(node3)(node)
	}
	cfg := config.NodeHealth{
		MaxConcurrency: testMaxConcurrency,
		ProbeCooldown:  time.Minute,
		Timeout:        time.Second,
	}
	m := newTestNodeHealthMonitor(t, cfg, ping)
	m.MarkUnhealthy(node3)
	_, ok := m.Probe(context.Background(), []hiero.AccountID{node3})
	require.True(t, ok)

	// when node 3 fails again right away
	m.MarkUnhealthy(node3)
	recovered, ok := m.Probe(context.Background(), []hiero.AccountID{node3})

	// then the probe pings it again
	assert.True(t, ok)
	assert.Equal(t, node3, recovered)
	assert.Equal(t, int32(2), pings.Load())
}

func TestStartPingsEveryNodeRightAway(t *testing.T) {
	// given a frequency so long that no tick happens during the test
	var pings atomic.Int32
	ping := func(node hiero.AccountID) error {
		pings.Add(1)
		return pingNodes(node3, node4)(node)
	}
	cfg := config.NodeHealth{Frequency: time.Hour, MaxConcurrency: testMaxConcurrency}
	m := newTestNodeHealthMonitor(t, cfg, ping)

	// when
	m.Start(t.Context())

	// then the first round pings every node, and node 5, which doesn't answer, is marked unhealthy
	assert.Eventually(t, func() bool {
		return pings.Load() == 3 && isUnhealthy(m, node5)
	}, time.Second, 10*time.Millisecond)
	assert.False(t, isUnhealthy(m, node3))
	assert.False(t, isUnhealthy(m, node4))
}

func TestStartAndStopGracefully(t *testing.T) {
	var pings atomic.Int32
	ping := func(hiero.AccountID) error {
		pings.Add(1)
		return errNodeDown
	}
	cfg := config.NodeHealth{Frequency: 50 * time.Millisecond, MaxConcurrency: testMaxConcurrency}
	m := newTestNodeHealthMonitor(t, cfg, ping)

	ctx, cancel := context.WithCancel(context.Background())
	m.Start(ctx)

	// Wait for more than the first round
	assert.Eventually(t, func() bool {
		return pings.Load() > 3
	}, time.Second, 10*time.Millisecond)

	cancel()

	// Wait a moment and confirm it stopped
	time.Sleep(100 * time.Millisecond)
	snapshot := pings.Load()
	time.Sleep(100 * time.Millisecond)
	assert.Equal(t, snapshot, pings.Load())
}

func TestNewNodeHealthMonitorCapsSdkBackoff(t *testing.T) {
	client := newTestClient(t, map[string]hiero.AccountID{"10.0.0.1:50211": node3})

	cfg := config.NodeHealth{Frequency: 10 * time.Second, MaxConcurrency: testMaxConcurrency, Timeout: 2 * time.Second}
	NewNodeHealthMonitor(client, cfg)

	assert.Equal(t, 5*time.Second, client.GetNodeMaxBackoff())
	assert.Equal(t, 5*time.Second, client.GetNodeMaxReadmitPeriod())
}

func TestPingReachesNodeOnceSdkBackoffElapses(t *testing.T) {
	// given an unhealthy node that failed and so is in SDK backoff, capped at 100ms
	consensusNode := newMockConsensusNode(t)
	client := newTestClient(t, map[string]hiero.AccountID{consensusNode.address: node3})
	cfg := config.NodeHealth{Frequency: 200 * time.Millisecond, MaxConcurrency: testMaxConcurrency}
	m := NewNodeHealthMonitor(client, cfg).(*nodeHealthMonitor)
	consensusNode.down.Store(true)
	require.Error(t, client.Ping(node3))
	m.MarkUnhealthy(node3)
	consensusNode.down.Store(false)

	// when the SDK backoff hasn't elapsed, the ping doesn't reach the node
	m.pingAllNodes(context.Background())
	assert.Equal(t, int32(1), consensusNode.requests.Load())
	assert.True(t, isUnhealthy(m, node3))

	// then the next ping after the backoff reaches the node and releases it
	assert.Eventually(t, func() bool {
		m.pingAllNodes(context.Background())
		return !isUnhealthy(m, node3)
	}, 2*time.Second, 10*time.Millisecond)
	assert.Equal(t, int32(2), consensusNode.requests.Load())
}

func TestPingTreatsPrecheckAnswerAsReachable(t *testing.T) {
	tests := []struct {
		name      string
		precheck  services.ResponseCodeEnum
		unhealthy bool
	}{
		{name: "ok", precheck: services.ResponseCodeEnum_OK},
		{name: "invalid account", precheck: services.ResponseCodeEnum_INVALID_ACCOUNT_ID},
		{name: "busy", precheck: services.ResponseCodeEnum_BUSY, unhealthy: true},
		{name: "platform not active", precheck: services.ResponseCodeEnum_PLATFORM_NOT_ACTIVE, unhealthy: true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			consensusNode := newMockConsensusNode(t)
			consensusNode.precheck.Store(int32(tt.precheck))
			client := newTestClient(t, map[string]hiero.AccountID{consensusNode.address: node3})
			cfg := config.NodeHealth{Frequency: 10 * time.Second, MaxConcurrency: testMaxConcurrency, Timeout: 2 * time.Second}
			m := NewNodeHealthMonitor(client, cfg).(*nodeHealthMonitor)
			m.MarkUnhealthy(node3)

			m.pingAllNodes(context.Background())

			assert.Equal(t, int32(1), consensusNode.requests.Load())
			assert.Equal(t, tt.unhealthy, isUnhealthy(m, node3))
		})
	}
}
