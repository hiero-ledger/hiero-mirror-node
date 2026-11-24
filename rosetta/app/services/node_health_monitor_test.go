// SPDX-License-Identifier: Apache-2.0

package services

import (
	"context"
	"crypto/ed25519"
	"fmt"
	"maps"
	"net"
	"slices"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-sdk-go/v2/proto/services"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	pkgErrors "github.com/pkg/errors"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

const testMaxConcurrency = 20

var (
	errNodeDown          = status.Error(codes.Unavailable, "node is down")
	errPayerNotFound     = hiero.ErrHederaPreCheckStatus{Status: hiero.StatusPayerAccountNotFound}
	node3                = hiero.AccountID{Account: 3}
	node4                = hiero.AccountID{Account: 4}
	node5                = hiero.AccountID{Account: 5}
	testNodeHealthConfig = config.NodeHealth{
		Enabled:         true,
		Frequency:       time.Hour,
		MaxConcurrency:  testMaxConcurrency,
		RecheckCooldown: time.Hour,
		Timeout:         2 * time.Second,
	}
)

// concurrencyTracker records the most checks in flight at once.
type concurrencyTracker struct {
	inFlight atomic.Int32
	max      atomic.Int32
}

func (c *concurrencyTracker) track(check func(node hiero.AccountID) error) func(node hiero.AccountID) error {
	return func(node hiero.AccountID) error {
		inFlight := c.inFlight.Add(1)
		defer c.inFlight.Add(-1)
		for observed := c.max.Load(); inFlight > observed; observed = c.max.Load() {
			if c.max.CompareAndSwap(observed, inFlight) {
				break
			}
		}
		return check(node)
	}
}

// mockConsensusNode is an in-process consensus node answering crypto transfers with the configured precheck status.
type mockConsensusNode struct {
	services.UnimplementedCryptoServiceServer
	address     string
	delay       atomic.Int64
	down        atomic.Bool
	precheck    atomic.Int32
	requests    atomic.Int32
	transaction atomic.Pointer[services.Transaction]
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
	ctx context.Context,
	transaction *services.Transaction,
) (*services.TransactionResponse, error) {
	n.requests.Add(1)
	n.transaction.Store(transaction)
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	case <-time.After(time.Duration(n.delay.Load())):
	}

	if n.down.Load() {
		return nil, errNodeDown
	}

	return &services.TransactionResponse{NodeTransactionPrecheckCode: services.ResponseCodeEnum(n.precheck.Load())}, nil
}

// checkNodes returns a check that the healthy nodes pass and the others fail.
func checkNodes(healthy ...hiero.AccountID) func(node hiero.AccountID) error {
	return func(node hiero.AccountID) error {
		if slices.Contains(healthy, node) {
			return errPayerNotFound
		}
		return errNodeDown
	}
}

// countChecks counts the calls to the check.
func countChecks(count *atomic.Int32, check func(node hiero.AccountID) error) func(node hiero.AccountID) error {
	return func(node hiero.AccountID) error {
		count.Add(1)
		return check(node)
	}
}

// gateChecks returns a check the passing nodes pass right away and the others fail once the gate opens.
func gateChecks(t *testing.T, passing ...hiero.AccountID) (func(node hiero.AccountID) error, func()) {
	gate := make(chan struct{})
	open := sync.OnceFunc(func() { close(gate) })
	t.Cleanup(open)
	return func(node hiero.AccountID) error {
		if slices.Contains(passing, node) {
			return errPayerNotFound
		}
		<-gate
		return errNodeDown
	}, open
}

func isUnhealthy(m *nodeHealthMonitor, node hiero.AccountID) bool {
	m.mutex.RLock()
	defer m.mutex.RUnlock()
	_, ok := m.unhealthyNodes[node.String()]
	return ok
}

func markAllUnhealthy(m *nodeHealthMonitor) {
	for _, node := range getNetworkNodes(m.client) {
		m.markUnhealthy(node, errNodeDown)
	}
}

func newTestClient(t *testing.T, network map[string]hiero.AccountID) *hiero.Client {
	client, err := hiero.ClientForNetworkV2(network)
	require.NoError(t, err)
	client.SetMaxAttempts(1)
	t.Cleanup(func() { _ = client.Close() })
	return client
}

// newTestNodeHealthMonitor creates a monitor, not started, for nodes 0.0.3, 0.0.4 and 0.0.5.
func newTestNodeHealthMonitor(
	t *testing.T,
	cfg config.NodeHealth,
	check func(node hiero.AccountID) error,
) *nodeHealthMonitor {
	client := newTestClient(t, map[string]hiero.AccountID{
		"10.0.0.1:50211": node3,
		"10.0.0.2:50211": node4,
		"10.0.0.3:50211": node5,
	})
	return newNodeHealthMonitor(t.Context(), client, cfg, check)
}

func TestGetHealthyNode(t *testing.T) {
	m := newTestNodeHealthMonitor(t, testNodeHealthConfig, checkNodes())
	m.markUnhealthy(node4, errNodeDown)

	picked := make(map[hiero.AccountID]int)
	for range 100 {
		node, rErr := m.GetHealthyNode(t.Context())
		require.Nil(t, rErr)
		picked[node]++
	}

	assert.ElementsMatch(t, []hiero.AccountID{node3, node5}, slices.Collect(maps.Keys(picked)))
}

func TestGetHealthyNodeDeduplicatesNodeAddresses(t *testing.T) {
	client := newTestClient(t, map[string]hiero.AccountID{
		"10.0.0.1:50211": node3,
		"10.0.0.1:50212": node3,
		"10.0.0.2:50211": node4,
	})
	m := newNodeHealthMonitor(t.Context(), client, testNodeHealthConfig, checkNodes())
	m.markUnhealthy(node4, errNodeDown)

	nodes, _ := m.getHealthyNodes()

	assert.Equal(t, []hiero.AccountID{node3}, nodes)
}

func TestGetHealthyNodeWhenAllUnhealthy(t *testing.T) {
	var checks atomic.Int32
	m := newTestNodeHealthMonitor(t, testNodeHealthConfig, countChecks(&checks, checkNodes(node4)))
	markAllUnhealthy(m)

	node, rErr := m.GetHealthyNode(t.Context())

	assert.Nil(t, rErr)
	assert.Equal(t, node4, node)
	assert.False(t, isUnhealthy(m, node4))
	assert.True(t, isUnhealthy(m, node3))
	assert.True(t, isUnhealthy(m, node5))
	assert.Eventually(t, func() bool { return checks.Load() == 3 }, time.Second, 10*time.Millisecond)
}

func TestGetHealthyNodeReturnsFirstRecoveredNode(t *testing.T) {
	// given node 3 recovers while the checks of nodes 4 and 5 hang
	check, _ := gateChecks(t, node3)
	m := newTestNodeHealthMonitor(t, testNodeHealthConfig, check)
	markAllUnhealthy(m)

	// when
	node, rErr := m.GetHealthyNode(t.Context())

	// then
	assert.Nil(t, rErr)
	assert.Equal(t, node3, node)
	m.mutex.RLock()
	defer m.mutex.RUnlock()
	assert.NotNil(t, m.roundDone)
}

func TestGetHealthyNodeWhenNoneRecovers(t *testing.T) {
	var checks atomic.Int32
	m := newTestNodeHealthMonitor(t, testNodeHealthConfig, countChecks(&checks, checkNodes()))
	markAllUnhealthy(m)

	node, rErr := m.GetHealthyNode(t.Context())

	assert.Equal(t, errors.ErrNodeAccountIdsEmpty, rErr)
	assert.Equal(t, hiero.AccountID{}, node)
	assert.Equal(t, int32(3), checks.Load())
}

func TestGetHealthyNodeWaitsAtMostTimeout(t *testing.T) {
	// given checks that hang
	check, _ := gateChecks(t)
	cfg := testNodeHealthConfig
	cfg.Timeout = 100 * time.Millisecond
	m := newTestNodeHealthMonitor(t, cfg, check)
	markAllUnhealthy(m)

	// when
	startedAt := time.Now()
	node, rErr := m.GetHealthyNode(t.Context())

	// then it gives up while the round is still in progress
	assert.Equal(t, errors.ErrNodeAccountIdsEmpty, rErr)
	assert.Equal(t, hiero.AccountID{}, node)
	assert.Less(t, time.Since(startedAt), time.Second)
	m.mutex.RLock()
	defer m.mutex.RUnlock()
	assert.NotNil(t, m.roundDone)
}

func TestGetHealthyNodeCoolsDown(t *testing.T) {
	// given
	var checks atomic.Int32
	cfg := testNodeHealthConfig
	cfg.RecheckCooldown = 200 * time.Millisecond
	m := newTestNodeHealthMonitor(t, cfg, countChecks(&checks, checkNodes()))
	markAllUnhealthy(m)
	_, rErr := m.GetHealthyNode(t.Context())
	require.Equal(t, errors.ErrNodeAccountIdsEmpty, rErr)
	require.Equal(t, int32(3), checks.Load())

	// then it fails fast within the cool-down
	_, rErr = m.GetHealthyNode(t.Context())
	assert.Equal(t, errors.ErrNodeAccountIdsEmpty, rErr)
	assert.Equal(t, int32(3), checks.Load())

	// then it checks the nodes again after the cool-down
	time.Sleep(cfg.RecheckCooldown)
	_, rErr = m.GetHealthyNode(t.Context())
	assert.Equal(t, errors.ErrNodeAccountIdsEmpty, rErr)
	assert.Equal(t, int32(6), checks.Load())
}

func TestGetHealthyNodeWhenContextDone(t *testing.T) {
	check, _ := gateChecks(t)
	m := newTestNodeHealthMonitor(t, testNodeHealthConfig, check)
	markAllUnhealthy(m)
	ctx, cancel := context.WithCancel(t.Context())
	cancel()

	node, rErr := m.GetHealthyNode(ctx)

	assert.Equal(t, errors.ErrNodeAccountIdsEmpty, rErr)
	assert.Equal(t, hiero.AccountID{}, node)
}

func TestGetHealthyNodeWhenDisabled(t *testing.T) {
	// given a node the SDK put into backoff, which the SDK doesn't list as healthy
	consensusNode := newMockConsensusNode(t)
	consensusNode.down.Store(true)
	client := newTestClient(t, map[string]hiero.AccountID{
		consensusNode.address: node3,
		"10.0.0.1:50211":      node4,
		"10.0.0.1:50212":      node4,
	})
	_, err := hiero.NewTransferTransaction().
		SetNodeAccountIDs([]hiero.AccountID{node3}).
		SetTransactionID(hiero.TransactionIDGenerate(hiero.AccountID{Account: 2})).
		Execute(client)
	require.Error(t, err)
	monitor, err := NewNodeHealthMonitor(t.Context(), client, &config.Mirror{}, true)
	require.NoError(t, err)

	// when
	nodes := getSdkHealthyNodes(client)
	node, rErr := monitor.GetHealthyNode(t.Context())

	// then
	assert.Equal(t, []hiero.AccountID{node4}, nodes)
	assert.Nil(t, rErr)
	assert.Equal(t, node4, node)
}

func TestRecordSubmitError(t *testing.T) {
	tests := []struct {
		name      string
		enabled   bool
		err       error
		unhealthy bool
	}{
		{name: "node error", enabled: true, err: errNodeDown, unhealthy: true},
		{
			name:    "user error",
			enabled: true,
			err:     hiero.ErrHederaPreCheckStatus{Status: hiero.StatusInsufficientPayerBalance},
		},
		{name: "disabled", err: errNodeDown},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			m := newTestNodeHealthMonitor(t, testNodeHealthConfig, checkNodes())
			m.enabled = tt.enabled

			m.RecordSubmitError(node3, tt.err)

			assert.Equal(t, tt.unhealthy, isUnhealthy(m, node3))
		})
	}
}

func TestMarkHealthyIgnoresCheckStartedBeforeLatestFailure(t *testing.T) {
	m := newTestNodeHealthMonitor(t, testNodeHealthConfig, checkNodes())
	checkStartedAt := time.Now().Add(-time.Second)
	m.markUnhealthy(node3, errNodeDown)

	// A check started before the failure doesn't vouch for the node
	m.markHealthy(node3, checkStartedAt)
	assert.True(t, isUnhealthy(m, node3))

	m.markHealthy(node3, time.Now())
	assert.False(t, isUnhealthy(m, node3))
}

func TestMarkUnhealthyRecordsLatestFailure(t *testing.T) {
	m := newTestNodeHealthMonitor(t, testNodeHealthConfig, checkNodes())
	m.unhealthyNodes[node3.String()] = time.Now().Add(-time.Minute)
	checkStartedAt := time.Now().Add(-time.Second)

	// The node fails again while the check is in flight
	m.markUnhealthy(node3, errNodeDown)
	m.markHealthy(node3, checkStartedAt)

	assert.True(t, isUnhealthy(m, node3))
}

func TestCheckAllNodes(t *testing.T) {
	// given
	node6 := hiero.AccountID{Account: 6}
	client := newTestClient(t, map[string]hiero.AccountID{
		"10.0.0.1:50211": node3,
		"10.0.0.2:50211": node4,
		"10.0.0.3:50211": node5,
		"10.0.0.4:50211": node6,
	})
	var checks atomic.Int32
	m := newNodeHealthMonitor(t.Context(), client, testNodeHealthConfig, countChecks(&checks, checkNodes(node3, node5)))
	m.markUnhealthy(node3, errNodeDown)
	m.markUnhealthy(node4, errNodeDown)

	// when
	m.checkAllNodes()

	// then
	assert.Equal(t, int32(4), checks.Load())
	assert.False(t, isUnhealthy(m, node3))
	assert.True(t, isUnhealthy(m, node4))
	assert.False(t, isUnhealthy(m, node5))
	assert.True(t, isUnhealthy(m, node6))
}

func TestCheckAllNodesForgetsNodesNotInNetwork(t *testing.T) {
	var checks atomic.Int32
	m := newTestNodeHealthMonitor(t, testNodeHealthConfig, countChecks(&checks, checkNodes()))
	staleNode := hiero.AccountID{Account: 999}
	m.markUnhealthy(staleNode, errNodeDown)

	m.checkAllNodes()

	assert.Equal(t, int32(3), checks.Load())
	assert.NotContains(t, m.unhealthyNodes, staleNode.String())
}

func TestCheckAllNodesLimitsConcurrency(t *testing.T) {
	// given
	network := make(map[string]hiero.AccountID)
	for i := range 6 {
		network[fmt.Sprintf("10.0.0.%d:50211", i+1)] = hiero.AccountID{Account: uint64(3 + i)}
	}
	var checks atomic.Int32
	var tracker concurrencyTracker
	check := tracker.track(func(hiero.AccountID) error {
		checks.Add(1)
		time.Sleep(20 * time.Millisecond)
		return errNodeDown
	})
	cfg := testNodeHealthConfig
	cfg.MaxConcurrency = 2
	m := newNodeHealthMonitor(t.Context(), newTestClient(t, network), cfg, check)

	// when
	m.checkAllNodes()

	// then
	assert.Equal(t, int32(len(network)), checks.Load())
	assert.Equal(t, int32(2), tracker.max.Load())
}

func TestCheckAllNodesWhenContextDone(t *testing.T) {
	var checks atomic.Int32
	client := newTestClient(t, map[string]hiero.AccountID{"10.0.0.1:50211": node3})
	ctx, cancel := context.WithCancel(t.Context())
	cancel()
	m := newNodeHealthMonitor(ctx, client, testNodeHealthConfig, countChecks(&checks, checkNodes()))

	m.checkAllNodes()

	assert.Zero(t, checks.Load())
}

func TestStartRoundJoinsRoundInProgress(t *testing.T) {
	var checks atomic.Int32
	check, open := gateChecks(t)
	m := newTestNodeHealthMonitor(t, testNodeHealthConfig, countChecks(&checks, check))

	roundDone := m.startRound(true)
	assert.Equal(t, roundDone, m.startRound(false))
	assert.Equal(t, roundDone, m.startRound(true))

	open()
	<-roundDone
	assert.Equal(t, int32(3), checks.Load())
}

func TestStartRoundCoolsDown(t *testing.T) {
	var checks atomic.Int32
	m := newTestNodeHealthMonitor(t, testNodeHealthConfig, countChecks(&checks, checkNodes()))
	<-m.startRound(true)

	// Within the cool-down, only a forced round starts
	assert.Nil(t, m.startRound(false))
	<-m.startRound(true)
	assert.Equal(t, int32(6), checks.Load())
}

func TestStartChecksEveryNodeRightAway(t *testing.T) {
	// given
	var checks atomic.Int32
	m := newTestNodeHealthMonitor(t, testNodeHealthConfig, countChecks(&checks, checkNodes(node3, node4)))

	// when
	m.start()

	// then
	assert.Eventually(t, func() bool {
		return checks.Load() == 3 && isUnhealthy(m, node5)
	}, time.Second, 10*time.Millisecond)
	assert.False(t, isUnhealthy(m, node3))
	assert.False(t, isUnhealthy(m, node4))
}

func TestStartAndStopGracefully(t *testing.T) {
	var checks atomic.Int32
	cfg := testNodeHealthConfig
	cfg.Frequency = 50 * time.Millisecond
	client := newTestClient(t, map[string]hiero.AccountID{"10.0.0.1:50211": node3})
	ctx, cancel := context.WithCancel(t.Context())
	m := newNodeHealthMonitor(ctx, client, cfg, countChecks(&checks, checkNodes()))
	m.start()

	assert.Eventually(t, func() bool {
		return checks.Load() > 1
	}, time.Second, 10*time.Millisecond)

	cancel()

	time.Sleep(100 * time.Millisecond)
	snapshot := checks.Load()
	time.Sleep(100 * time.Millisecond)
	assert.Equal(t, snapshot, checks.Load())
}

func TestNewNodeHealthMonitor(t *testing.T) {
	// given
	consensusNode := newMockConsensusNode(t)
	consensusNode.precheck.Store(int32(services.ResponseCodeEnum_PAYER_ACCOUNT_NOT_FOUND))
	node := hiero.AccountID{Shard: 1, Realm: 2, Account: 3}
	client := newTestClient(t, map[string]hiero.AccountID{consensusNode.address: node})
	mirrorConfig := &config.Mirror{
		Common:  config.CommonConfig{Realm: 2, Shard: 1},
		Rosetta: config.Config{NodeHealth: testNodeHealthConfig},
	}

	// when
	monitor, err := NewNodeHealthMonitor(t.Context(), client, mirrorConfig, true)

	// then
	require.NoError(t, err)
	m := monitor.(*nodeHealthMonitor)
	assert.True(t, m.enabled)
	assert.Equal(t, testNodeHealthConfig.Frequency/2, client.GetNodeMaxBackoff())
	require.Eventually(t, func() bool {
		return consensusNode.requests.Load() == 1
	}, 5*time.Second, 10*time.Millisecond)
	assert.Equal(t, hiero.AccountID{Shard: 1, Realm: 2, Account: healthCheckPayerNum}, getPayer(t, consensusNode))
	assert.False(t, isUnhealthy(m, node))
}

func TestNewNodeHealthMonitorWhenDisabled(t *testing.T) {
	sdkDefaults := newTestClient(t, map[string]hiero.AccountID{"10.0.0.1:50211": node3})
	tests := []struct {
		name    string
		enabled bool
		online  bool
	}{
		{name: "disabled", online: true},
		{name: "offline", enabled: true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			client := newTestClient(t, map[string]hiero.AccountID{"10.0.0.1:50211": node3})
			nodeHealth := testNodeHealthConfig
			nodeHealth.Enabled = tt.enabled
			mirrorConfig := &config.Mirror{Rosetta: config.Config{NodeHealth: nodeHealth}}

			monitor, err := NewNodeHealthMonitor(t.Context(), client, mirrorConfig, tt.online)

			require.NoError(t, err)
			assert.False(t, monitor.(*nodeHealthMonitor).enabled)
			assert.Equal(t, sdkDefaults.GetNodeMaxBackoff(), client.GetNodeMaxBackoff())
		})
	}
}

func TestHealthCheck(t *testing.T) {
	tests := []struct {
		name     string
		down     bool
		precheck services.ResponseCodeEnum
		healthy  bool
	}{
		{name: "payer not found", precheck: services.ResponseCodeEnum_PAYER_ACCOUNT_NOT_FOUND, healthy: true},
		{name: "down", down: true},
		{name: "ok", precheck: services.ResponseCodeEnum_OK},
		{name: "busy", precheck: services.ResponseCodeEnum_BUSY},
		{name: "platform not active", precheck: services.ResponseCodeEnum_PLATFORM_NOT_ACTIVE},
		{name: "invalid node account", precheck: services.ResponseCodeEnum_INVALID_NODE_ACCOUNT},
		{name: "invalid transaction start", precheck: services.ResponseCodeEnum_INVALID_TRANSACTION_START},
		{name: "transaction expired", precheck: services.ResponseCodeEnum_TRANSACTION_EXPIRED},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// given
			consensusNode := newMockConsensusNode(t)
			consensusNode.down.Store(tt.down)
			consensusNode.precheck.Store(int32(tt.precheck))
			client := newTestClient(t, map[string]hiero.AccountID{consensusNode.address: node3})
			key, err := hiero.PrivateKeyGenerateEd25519()
			require.NoError(t, err)
			payer := hiero.AccountID{Account: healthCheckPayerNum}
			m := newNodeHealthMonitor(t.Context(), client, testNodeHealthConfig, newHealthCheck(client, payer, key, time.Second))
			m.markUnhealthy(node3, errNodeDown)

			// when
			m.checkAllNodes()

			// then
			assert.Equal(t, !tt.healthy, isUnhealthy(m, node3))
			require.Equal(t, int32(1), consensusNode.requests.Load())
			signedTransaction := getSignedTransaction(t, consensusNode)
			body := services.TransactionBody{}
			require.NoError(t, proto.Unmarshal(signedTransaction.BodyBytes, &body))
			assert.Equal(t, payer, getPayer(t, consensusNode))
			assert.Equal(t, uint64(3), uint64(body.GetNodeAccountID().GetAccountNum()))
			require.NotNil(t, body.GetCryptoTransfer())
			assert.Empty(t, body.GetCryptoTransfer().GetTransfers().GetAccountAmounts())
			assert.Empty(t, body.GetCryptoTransfer().GetTokenTransfers())
			sigPairs := signedTransaction.GetSigMap().GetSigPair()
			require.Len(t, sigPairs, 1)
			assert.Equal(t, key.PublicKey().BytesRaw(), sigPairs[0].GetPubKeyPrefix())
			assert.True(t, ed25519.Verify(key.PublicKey().BytesRaw(), signedTransaction.BodyBytes, sigPairs[0].GetEd25519()))
		})
	}
}

func TestHealthCheckTimesOut(t *testing.T) {
	// given
	consensusNode := newMockConsensusNode(t)
	consensusNode.delay.Store(int64(5 * time.Second))
	client := newTestClient(t, map[string]hiero.AccountID{consensusNode.address: node3})
	key, err := hiero.PrivateKeyGenerateEd25519()
	require.NoError(t, err)
	check := newHealthCheck(client, hiero.AccountID{Account: healthCheckPayerNum}, key, 100*time.Millisecond)

	// when
	startedAt := time.Now()
	err = check(node3)

	// then
	assert.Equal(t, codes.DeadlineExceeded, status.Code(err))
	assert.Less(t, time.Since(startedAt), time.Second)
}

func TestHealthCheckReachesNodeOnceSdkBackoffElapses(t *testing.T) {
	// given a node in SDK backoff
	consensusNode := newMockConsensusNode(t)
	consensusNode.down.Store(true)
	consensusNode.precheck.Store(int32(services.ResponseCodeEnum_PAYER_ACCOUNT_NOT_FOUND))
	client := newTestClient(t, map[string]hiero.AccountID{consensusNode.address: node3})
	client.SetNodeMaxBackoff(100 * time.Millisecond)
	key, err := hiero.PrivateKeyGenerateEd25519()
	require.NoError(t, err)
	check := newHealthCheck(client, hiero.AccountID{Account: healthCheckPayerNum}, key, time.Second)
	m := newNodeHealthMonitor(t.Context(), client, testNodeHealthConfig, check)
	m.checkAllNodes()
	require.True(t, isUnhealthy(m, node3))
	consensusNode.down.Store(false)

	// when the SDK backoff hasn't elapsed, the check doesn't reach the node
	m.checkAllNodes()
	assert.Equal(t, int32(1), consensusNode.requests.Load())
	assert.True(t, isUnhealthy(m, node3))

	// then the next check after the backoff reaches the node
	assert.Eventually(t, func() bool {
		m.checkAllNodes()
		return !isUnhealthy(m, node3)
	}, 2*time.Second, 10*time.Millisecond)
	assert.Equal(t, int32(2), consensusNode.requests.Load())
}

func TestIsHealthy(t *testing.T) {
	tests := []struct {
		name     string
		err      error
		expected bool
	}{
		{name: "payer not found", err: errPayerNotFound, expected: true},
		{name: "wrapped payer not found", err: pkgErrors.Wrapf(errPayerNotFound, "retry 1/1"), expected: true},
		{name: "accepted"},
		{name: "busy", err: hiero.ErrHederaPreCheckStatus{Status: hiero.StatusBusy}},
		{
			name: "wrapped platform not active",
			err:  pkgErrors.Wrapf(hiero.ErrHederaPreCheckStatus{Status: hiero.StatusPlatformNotActive}, "retry 1/1"),
		},
		{name: "invalid signature", err: hiero.ErrHederaPreCheckStatus{Status: hiero.StatusInvalidSignature}},
		{name: "unavailable", err: errNodeDown},
		{name: "in sdk backoff", err: fmt.Errorf("unknown error occurred after max attempts")},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, isHealthy(tt.err))
		})
	}
}

func TestIsNodeError(t *testing.T) {
	busy := hiero.ErrHederaPreCheckStatus{Status: hiero.StatusBusy}
	insufficientPayerBalance := hiero.ErrHederaPreCheckStatus{Status: hiero.StatusInsufficientPayerBalance}
	tests := []struct {
		name     string
		err      error
		expected bool
	}{
		{name: "nil"},
		{name: "insufficient payer balance", err: insufficientPayerBalance},
		{name: "invalid signature", err: hiero.ErrHederaPreCheckStatus{Status: hiero.StatusInvalidSignature}},
		{name: "busy", err: busy, expected: true},
		{
			name:     "platform not active",
			err:      hiero.ErrHederaPreCheckStatus{Status: hiero.StatusPlatformNotActive},
			expected: true,
		},
		{
			name:     "platform transaction not created",
			err:      hiero.ErrHederaPreCheckStatus{Status: hiero.StatusPlatformTransactionNotCreated},
			expected: true,
		},
		{
			name:     "invalid node account",
			err:      hiero.ErrHederaPreCheckStatus{Status: hiero.StatusInvalidNodeAccount},
			expected: true,
		},
		// The SDK wraps the status of the last attempt when it gives up retrying
		{name: "wrapped busy", err: pkgErrors.Wrapf(busy, "retry %d/%d", 1, 1), expected: true},
		{name: "wrapped user error", err: pkgErrors.Wrapf(insufficientPayerBalance, "retry %d/%d", 1, 1)},
		{
			name:     "unavailable",
			err:      pkgErrors.Wrapf(status.Error(codes.Unavailable, "connection refused"), "retry %d/%d", 1, 1),
			expected: true,
		},
		{name: "deadline exceeded", err: status.Error(codes.DeadlineExceeded, "timeout"), expected: true},
		{name: "resource exhausted", err: status.Error(codes.ResourceExhausted, "dial timeout"), expected: true},
		{name: "internal", err: status.Error(codes.Internal, "stream reset"), expected: true},
		{name: "invalid argument", err: status.Error(codes.InvalidArgument, "bad request")},
		// Errors the SDK raises before contacting the node
		{name: "unknown node", err: hiero.ErrInvalidNodeAccountIDSet{NodeAccountID: nodeAccountId}},
		{name: "batch key", err: fmt.Errorf("cannot execute batchified transaction outside of BatchTransaction")},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, isNodeError(tt.err))
		})
	}
}

func getPayer(t *testing.T, consensusNode *mockConsensusNode) hiero.AccountID {
	body := services.TransactionBody{}
	require.NoError(t, proto.Unmarshal(getSignedTransaction(t, consensusNode).BodyBytes, &body))
	payer := body.GetTransactionID().GetAccountID()
	return hiero.AccountID{
		Shard:   uint64(payer.GetShardNum()),
		Realm:   uint64(payer.GetRealmNum()),
		Account: uint64(payer.GetAccountNum()),
	}
}

func getSignedTransaction(t *testing.T, consensusNode *mockConsensusNode) *services.SignedTransaction {
	transaction := consensusNode.transaction.Load()
	require.NotNil(t, transaction)
	signedTransaction := &services.SignedTransaction{}
	require.NoError(t, proto.Unmarshal(transaction.GetSignedTransactionBytes(), signedTransaction))
	return signedTransaction
}
