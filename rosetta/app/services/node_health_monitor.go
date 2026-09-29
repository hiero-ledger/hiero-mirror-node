// SPDX-License-Identifier: Apache-2.0

package services

import (
	"context"
	stdErrors "errors"
	"maps"
	"slices"
	"sync"
	"time"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
	"golang.org/x/sync/semaphore"
	"golang.org/x/sync/singleflight"
)

const probeKey = "probe"

// NodeHealthMonitor pings every node each tick. A node that fails a ping or a submission is marked unhealthy until it
// answers a ping.
type NodeHealthMonitor interface {
	FilterHealthy(nodes []hiero.AccountID) []hiero.AccountID
	MarkUnhealthy(node hiero.AccountID)
	Probe(ctx context.Context, candidates []hiero.AccountID) (hiero.AccountID, bool)
	Start(ctx context.Context)
}

type nodeHealthMonitor struct {
	client         *hiero.Client
	frequency      time.Duration
	mutex          sync.RWMutex
	ping           func(node hiero.AccountID) error
	pingSlots      *semaphore.Weighted
	probeAfter     time.Time
	probeCooldown  time.Duration
	probeGroup     singleflight.Group
	timeout        time.Duration
	unhealthyNodes map[string]unhealthyNode
}

type probeResult struct {
	node hiero.AccountID
	ok   bool
}

type unhealthyNode struct {
	failedAt time.Time
	id       hiero.AccountID
}

// NewNodeHealthMonitor creates a monitor for the nodes of the client. The SDK doesn't send any request to a node in
// backoff, so the monitor caps the client's per-node backoff at half its ping frequency, which normally gets a node that
// failed a ping out of backoff by the next tick. It caps the readmission period at the same value, because a node that
// fails right after the SDK found every node healthy is only listed again a readmission period after that scan.
func NewNodeHealthMonitor(client *hiero.Client, config config.NodeHealth) NodeHealthMonitor {
	m := newNodeHealthMonitor(client, config, client.Ping)
	backoffCap := m.frequency / 2
	client.SetNodeMaxBackoff(backoffCap)
	client.SetNodeMaxReadmitPeriod(backoffCap)
	return m
}

func newNodeHealthMonitor(
	client *hiero.Client,
	config config.NodeHealth,
	ping func(node hiero.AccountID) error,
) *nodeHealthMonitor {
	return &nodeHealthMonitor{
		client:         client,
		frequency:      config.Frequency,
		ping:           ping,
		pingSlots:      semaphore.NewWeighted(int64(config.MaxConcurrency)),
		probeCooldown:  config.ProbeCooldown,
		timeout:        config.Timeout,
		unhealthyNodes: make(map[string]unhealthyNode),
	}
}

// FilterHealthy returns the nodes that aren't marked unhealthy.
func (m *nodeHealthMonitor) FilterHealthy(nodes []hiero.AccountID) []hiero.AccountID {
	m.mutex.RLock()
	defer m.mutex.RUnlock()

	if len(m.unhealthyNodes) == 0 {
		return nodes
	}

	healthy := make([]hiero.AccountID, 0, len(nodes))
	for _, node := range nodes {
		if _, ok := m.unhealthyNodes[node.String()]; !ok {
			healthy = append(healthy, node)
		}
	}
	return healthy
}

// MarkUnhealthy marks the node unhealthy until a ping started after this failure succeeds.
func (m *nodeHealthMonitor) MarkUnhealthy(node hiero.AccountID) {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	key := node.String()
	_, alreadyUnhealthy := m.unhealthyNodes[key]
	m.unhealthyNodes[key] = unhealthyNode{failedAt: time.Now(), id: node}
	if alreadyUnhealthy {
		return
	}

	log.Warnf("Marked node %s as unhealthy", node)
}

// Probe pings the candidates and returns the first one to answer within the timeout. It stops starting pings once a
// candidate answers, every ping already in flight that answers still marks its node healthy, and concurrent calls share
// a single round of pings. After a round finds no node, Probe fails fast without pinging until the cool-down elapses.
func (m *nodeHealthMonitor) Probe(ctx context.Context, candidates []hiero.AccountID) (hiero.AccountID, bool) {
	if len(candidates) == 0 {
		return hiero.AccountID{}, false
	}

	results := m.probeGroup.DoChan(probeKey, func() (any, error) {
		// Rounds never overlap, so probeAfter needs no lock
		if time.Now().Before(m.probeAfter) {
			log.Debugf("Skipping probe for another %s, since the last one found no node",
				time.Until(m.probeAfter).Round(time.Millisecond))
			return probeResult{}, nil
		}

		node, ok := m.probe(candidates)
		if !ok {
			m.probeAfter = time.Now().Add(m.probeCooldown)
		}
		return probeResult{node: node, ok: ok}, nil
	})

	select {
	case <-ctx.Done():
		return hiero.AccountID{}, false
	case result := <-results:
		probed := result.Val.(probeResult)
		return probed.node, probed.ok
	}
}

// Start pings every node right away and then every tick, until the context is done.
func (m *nodeHealthMonitor) Start(ctx context.Context) {
	go func() {
		log.Infof("Starting node health monitor with frequency %s", m.frequency)
		ticker := time.NewTicker(m.frequency)
		defer ticker.Stop()

		for {
			m.pingAllNodes(ctx)

			select {
			case <-ctx.Done():
				log.Info("Stopping node health monitor")
				return
			case <-ticker.C:
			}
		}
	}()
}

// forgetNodesNotIn forgets the unhealthy nodes that aren't in the network.
func (m *nodeHealthMonitor) forgetNodesNotIn(network map[string]hiero.AccountID) {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	for key := range m.unhealthyNodes {
		if _, ok := network[key]; !ok {
			delete(m.unhealthyNodes, key)
		}
	}
}

// markHealthy marks the node healthy, unless it failed again after the ping vouching for it started.
func (m *nodeHealthMonitor) markHealthy(node hiero.AccountID, pingStartedAt time.Time) {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	key := node.String()
	unhealthy, ok := m.unhealthyNodes[key]
	if !ok || unhealthy.failedAt.After(pingStartedAt) {
		return
	}

	delete(m.unhealthyNodes, key)
	log.Infof("Marked node %s as healthy", node)
}

// pingAllNodes pings every node in the address book, and forgets the unhealthy nodes no longer in it.
func (m *nodeHealthMonitor) pingAllNodes(ctx context.Context) {
	network := make(map[string]hiero.AccountID)
	for _, node := range m.client.GetNetwork() {
		network[node.String()] = node
	}

	m.forgetNodesNotIn(network)
	m.pingNodes(ctx, slices.Collect(maps.Values(network)), nil)
}

// pingNode pings the node, marking it healthy if it answers and unhealthy if it doesn't.
func (m *nodeHealthMonitor) pingNode(node hiero.AccountID) bool {
	startedAt := time.Now()
	err := m.ping(node)
	if !isReachable(err) {
		log.Debugf("Node %s didn't answer the ping: %s", node, err)
		m.MarkUnhealthy(node)
		return false
	}

	m.markHealthy(node, startedAt)
	return true
}

// pingNodes pings the nodes and calls onRecovered, if set, for each one that answers. Every ping holds one of the
// monitor's ping slots, so across all callers no more than maxConcurrency nodes are pinged at once. It stops starting
// pings once the context is done and returns when the started ones finish.
func (m *nodeHealthMonitor) pingNodes(
	ctx context.Context,
	nodes []hiero.AccountID,
	onRecovered func(node hiero.AccountID),
) {
	var wg sync.WaitGroup
	for _, node := range nodes {
		if m.pingSlots.Acquire(ctx, 1) != nil {
			break
		}

		wg.Go(func() {
			defer m.pingSlots.Release(1)
			if m.pingNode(node) && onRecovered != nil {
				onRecovered(node)
			}
		})
	}
	wg.Wait()
}

func (m *nodeHealthMonitor) probe(candidates []hiero.AccountID) (hiero.AccountID, bool) {
	// Canceled on return, so no more pings start once a candidate answers or the timeout elapses
	ctx, cancel := context.WithTimeout(context.Background(), m.timeout)
	defer cancel()

	// Buffered so pings that answer after the probe returns don't block
	recovered := make(chan hiero.AccountID, len(candidates))
	go func() {
		m.pingNodes(ctx, candidates, func(node hiero.AccountID) { recovered <- node })
		close(recovered)
	}()

	select {
	case node, ok := <-recovered:
		return node, ok
	case <-ctx.Done():
		log.Debugf("No unhealthy node answered the probe within %s", m.timeout)
		return hiero.AccountID{}, false
	}
}

// isReachable reports whether a ping result proves the node is up. Any precheck answer other than a node-level status
// does, e.g. INVALID_ACCOUNT_ID when the pinged treasury account doesn't exist in the network's shard and realm.
func isReachable(err error) bool {
	if err == nil {
		return true
	}

	preCheckErr, ok := stdErrors.AsType[hiero.ErrHederaPreCheckStatus](err)
	return ok && !isNodeStatus(preCheckErr.Status)
}
