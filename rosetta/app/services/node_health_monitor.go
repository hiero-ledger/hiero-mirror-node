// SPDX-License-Identifier: Apache-2.0

package services

import (
	"context"
	"crypto/rand"
	stdErrors "errors"
	"math/big"
	"sync"
	"time"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence/domain"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
	"golang.org/x/sync/semaphore"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// healthCheckPayerNum is the HTS system contract, whose number is never assigned to an account.
const healthCheckPayerNum = 359

// NodeHealthMonitor tracks the health of the nodes and picks a healthy one to submit a transaction to.
type NodeHealthMonitor interface {
	GetHealthyNode(ctx context.Context) (hiero.AccountID, *rTypes.Error)
	RecordSubmitError(node hiero.AccountID, err error)
}

type nodeHealthMonitor struct {
	check           func(node hiero.AccountID) error
	client          *hiero.Client
	ctx             context.Context
	enabled         bool
	frequency       time.Duration
	maxConcurrency  int64
	mutex           sync.RWMutex
	recheckCooldown time.Duration
	recovered       chan struct{} // closed and replaced when a node becomes healthy
	roundDone       chan struct{} // nil unless a round of health checks is in progress
	roundStartedAt  time.Time
	timeout         time.Duration
	unhealthyNodes  map[string]time.Time // node to the time of its last failure
}

// NewNodeHealthMonitor creates a monitor that, when online and enabled, checks the health of every node right away and
// then every tick. Otherwise, it picks a random node the SDK considers healthy.
func NewNodeHealthMonitor(
	ctx context.Context,
	client *hiero.Client,
	mirrorConfig *config.Mirror,
	online bool,
) (NodeHealthMonitor, error) {
	nodeHealth := mirrorConfig.Rosetta.NodeHealth
	if !online || !nodeHealth.Enabled {
		return &nodeHealthMonitor{client: client}, nil
	}

	payer, err := types.NewAccountIdFromEntityId(domain.EntityId{
		EntityNum: healthCheckPayerNum,
		RealmNum:  mirrorConfig.Common.Realm,
		ShardNum:  mirrorConfig.Common.Shard,
	}).ToSdkAccountId()
	if err != nil {
		return nil, err
	}

	// generate a key to sign the health check transaction
	key, err := hiero.PrivateKeyGenerateEd25519()
	if err != nil {
		return nil, err
	}

	m := newNodeHealthMonitor(ctx, client, nodeHealth, newHealthCheck(client, payer, key, nodeHealth.Timeout))
	// The SDK sends nothing to a node in backoff, so a node that failed a check must be out of it by the next tick
	client.SetNodeMaxBackoff(nodeHealth.Frequency / 2)
	m.start()
	return m, nil
}

func newNodeHealthMonitor(
	ctx context.Context,
	client *hiero.Client,
	nodeHealth config.NodeHealth,
	check func(node hiero.AccountID) error,
) *nodeHealthMonitor {
	return &nodeHealthMonitor{
		check:           check,
		client:          client,
		ctx:             ctx,
		enabled:         true,
		frequency:       nodeHealth.Frequency,
		maxConcurrency:  int64(nodeHealth.MaxConcurrency),
		recheckCooldown: nodeHealth.RecheckCooldown,
		recovered:       make(chan struct{}),
		timeout:         nodeHealth.Timeout,
		unhealthyNodes:  make(map[string]time.Time),
	}
}

// GetHealthyNode returns a random healthy node. If every node is unhealthy, it waits up to the timeout for a node to
// recover in a round of health checks, unless the last round started within the cool-down.
func (m *nodeHealthMonitor) GetHealthyNode(ctx context.Context) (hiero.AccountID, *rTypes.Error) {
	if !m.enabled {
		return pickRandomNode(getSdkHealthyNodes(m.client))
	}

	nodes, recovered := m.getHealthyNodes()
	if len(nodes) != 0 {
		return pickRandomNode(nodes)
	}

	roundDone := m.startRound(false)
	if roundDone == nil {
		log.Debug("Every node is unhealthy and the last round of health checks started within the cool-down")
		return hiero.AccountID{}, errors.ErrNodeAccountIdsEmpty
	}

	timer := time.NewTimer(m.timeout)
	defer timer.Stop()
	for {
		select {
		case <-ctx.Done():
			return hiero.AccountID{}, errors.ErrNodeAccountIdsEmpty
		case <-recovered:
			if nodes, recovered = m.getHealthyNodes(); len(nodes) != 0 {
				return pickRandomNode(nodes)
			}
			continue
		case <-roundDone:
		case <-timer.C:
		}

		nodes, _ = m.getHealthyNodes()
		return pickRandomNode(nodes)
	}
}

// RecordSubmitError marks the node unhealthy if the submission failed because of the node.
func (m *nodeHealthMonitor) RecordSubmitError(node hiero.AccountID, err error) {
	if !m.enabled || !isNodeError(err) {
		return
	}

	m.markUnhealthy(node, err)
}

// checkAllNodes checks every node in the address book, at most maxConcurrency at once, and forgets the unhealthy nodes
// no longer in it.
func (m *nodeHealthMonitor) checkAllNodes() {
	network := getNetworkNodes(m.client)
	m.forgetNodesNotIn(network)

	slots := semaphore.NewWeighted(m.maxConcurrency)
	var wg sync.WaitGroup
	for _, node := range network {
		if slots.Acquire(m.ctx, 1) != nil {
			break
		}

		wg.Go(func() {
			defer slots.Release(1)
			m.checkNode(node)
		})
	}
	wg.Wait()
}

func (m *nodeHealthMonitor) checkNode(node hiero.AccountID) {
	startedAt := time.Now()
	if err := m.check(node); !isHealthy(err) {
		m.markUnhealthy(node, err)
		return
	}

	m.markHealthy(node, startedAt)
}

func (m *nodeHealthMonitor) forgetNodesNotIn(network map[string]hiero.AccountID) {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	for key := range m.unhealthyNodes {
		if _, ok := network[key]; !ok {
			delete(m.unhealthyNodes, key)
		}
	}
}

// getHealthyNodes returns the nodes not marked unhealthy, and a channel closed when another node becomes healthy.
func (m *nodeHealthMonitor) getHealthyNodes() ([]hiero.AccountID, <-chan struct{}) {
	network := getNetworkNodes(m.client)

	m.mutex.RLock()
	defer m.mutex.RUnlock()

	healthy := make([]hiero.AccountID, 0, len(network))
	for key, node := range network {
		if _, ok := m.unhealthyNodes[key]; !ok {
			healthy = append(healthy, node)
		}
	}
	return healthy, m.recovered
}

// markHealthy marks the node healthy, unless it failed again after the check vouching for it started.
func (m *nodeHealthMonitor) markHealthy(node hiero.AccountID, checkStartedAt time.Time) {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	key := node.String()
	failedAt, ok := m.unhealthyNodes[key]
	if !ok || failedAt.After(checkStartedAt) {
		return
	}

	delete(m.unhealthyNodes, key)
	close(m.recovered)
	m.recovered = make(chan struct{})
	log.Infof("Marked node %s as healthy", node)
}

// markUnhealthy marks the node unhealthy until a check started after this failure passes.
func (m *nodeHealthMonitor) markUnhealthy(node hiero.AccountID, reason error) {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	key := node.String()
	_, alreadyUnhealthy := m.unhealthyNodes[key]
	m.unhealthyNodes[key] = time.Now()
	if alreadyUnhealthy {
		log.Debugf("Node %s is still unhealthy: %s", node, reason)
		return
	}

	log.Warnf("Marked node %s as unhealthy: %s", node, reason)
}

func (m *nodeHealthMonitor) start() {
	go func() {
		log.Infof("Starting node health monitor with frequency %s", m.frequency)
		ticker := time.NewTicker(m.frequency)
		defer ticker.Stop()

		for {
			<-m.startRound(true)

			select {
			case <-m.ctx.Done():
				log.Info("Stopping node health monitor")
				return
			case <-ticker.C:
			}
		}
	}()
}

// startRound starts a round of health checks, unless one is in progress, and returns a channel closed when it ends.
// Unless forced, it returns nil within the cool-down of the last round.
func (m *nodeHealthMonitor) startRound(force bool) <-chan struct{} {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	if m.roundDone != nil {
		return m.roundDone
	}

	if !force && time.Since(m.roundStartedAt) < m.recheckCooldown {
		return nil
	}

	roundDone := make(chan struct{})
	m.roundDone = roundDone
	m.roundStartedAt = time.Now()
	go func() {
		m.checkAllNodes()

		m.mutex.Lock()
		m.roundDone = nil
		m.mutex.Unlock()
		close(roundDone)
	}()
	return roundDone
}

// getNetworkNodes returns the distinct nodes in the address book, which lists a node once per address.
func getNetworkNodes(client *hiero.Client) map[string]hiero.AccountID {
	network := make(map[string]hiero.AccountID)
	for _, node := range client.GetNetwork() {
		network[node.String()] = node
	}
	return network
}

// getSdkHealthyNodes returns the distinct nodes the SDK considers healthy, which it lists once per healthy address.
func getSdkHealthyNodes(client *hiero.Client) []hiero.AccountID {
	transaction, err := hiero.NewTransferTransaction().
		SetTransactionID(hiero.TransactionIDGenerate(hiero.AccountID{Account: 2})).
		FreezeWith(client)
	if err != nil {
		log.Errorf("Failed to get the healthy nodes from the SDK: %s", err)
		return nil
	}

	nodes := transaction.GetNodeAccountIDs()
	distinct := make([]hiero.AccountID, 0, len(nodes))
	seen := make(map[string]struct{}, len(nodes))
	for _, node := range nodes {
		key := node.String()
		if _, ok := seen[key]; ok {
			continue
		}

		seen[key] = struct{}{}
		distinct = append(distinct, node)
	}
	return distinct
}

// isHealthy reports whether a health check got PAYER_ACCOUNT_NOT_FOUND, which a node only answers with once its
// readiness, node account, time and throttle checks pass.
func isHealthy(err error) bool {
	preCheckErr, ok := stdErrors.AsType[hiero.ErrHederaPreCheckStatus](err)
	return ok && preCheckErr.Status == hiero.StatusPayerAccountNotFound
}

// isNodeError reports whether a submission failed because of the node rather than the transaction. Errors the SDK
// raises locally, before contacting the node, don't count, since anyone can submit such a transaction.
func isNodeError(err error) bool {
	if preCheckErr, ok := stdErrors.AsType[hiero.ErrHederaPreCheckStatus](err); ok {
		switch preCheckErr.Status {
		case hiero.StatusBusy,
			hiero.StatusInvalidNodeAccount,
			hiero.StatusPlatformNotActive,
			hiero.StatusPlatformTransactionNotCreated:
			return true
		default:
			return false
		}
	}

	switch status.Code(err) {
	case codes.DeadlineExceeded, codes.Internal, codes.ResourceExhausted, codes.Unavailable:
		return true
	default:
		return false
	}
}

// newHealthCheck returns a health check that submits an empty crypto transfer paid by the payer and signed by the key.
func newHealthCheck(
	client *hiero.Client,
	payer hiero.AccountID,
	key hiero.PrivateKey,
	timeout time.Duration,
) func(node hiero.AccountID) error {
	return func(node hiero.AccountID) error {
		transaction, err := hiero.NewTransferTransaction().
			SetGrpcDeadline(&timeout).
			SetNodeAccountIDs([]hiero.AccountID{node}).
			SetTransactionID(hiero.TransactionIDGenerate(payer)).
			FreezeWith(client)
		if err != nil {
			return err
		}

		_, err = transaction.Sign(key).Execute(client)
		return err
	}
}

func pickRandomNode(nodes []hiero.AccountID) (hiero.AccountID, *rTypes.Error) {
	if len(nodes) == 0 {
		return hiero.AccountID{}, errors.ErrNodeAccountIdsEmpty
	}

	index, err := rand.Int(rand.Reader, big.NewInt(int64(len(nodes))))
	if err != nil {
		log.Errorf("Failed to get a random number, use 0 instead: %s", err)
		return nodes[0], nil
	}

	return nodes[index.Int64()], nil
}
