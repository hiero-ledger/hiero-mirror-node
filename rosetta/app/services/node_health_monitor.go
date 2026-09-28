// SPDX-License-Identifier: Apache-2.0

package services

import (
	"context"
	"sync"
	"time"

	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
)

const (
	defaultHealthCheckFrequency = 30 * time.Second
	defaultHealthCheckTimeout   = 2 * time.Second
)

type NodeHealthMonitor interface {
	FilterHealthy(nodes []hiero.AccountID) []hiero.AccountID
	MarkHealthy(node hiero.AccountID)
	MarkUnhealthy(node hiero.AccountID)
	Probe(ctx context.Context, candidates []hiero.AccountID) (hiero.AccountID, bool)
	Start(ctx context.Context)
}

type nodeHealthMonitor struct {
	client         *hiero.Client
	config         config.NodeHealth
	freeze         func(client *hiero.Client) ([]hiero.AccountID, error)
	mutex          sync.RWMutex
	ping           func(nodeID hiero.AccountID) error
	unhealthyNodes map[string]hiero.AccountID
}

func defaultFreeze(client *hiero.Client) ([]hiero.AccountID, error) {
	tx, err := hiero.NewTransferTransaction().
		SetTransactionID(hiero.TransactionIDGenerate(hiero.AccountID{Account: 2})).
		FreezeWith(client)
	if err != nil {
		return nil, err
	}
	return tx.GetNodeAccountIDs(), nil
}

func defaultPing(client *hiero.Client) func(nodeID hiero.AccountID) error {
	return func(nodeID hiero.AccountID) error {
		return client.Ping(nodeID)
	}
}

func NewNodeHealthMonitor(client *hiero.Client, config config.NodeHealth) NodeHealthMonitor {
	return &nodeHealthMonitor{
		client:         client,
		config:         config,
		freeze:         defaultFreeze,
		ping:           defaultPing(client),
		unhealthyNodes: make(map[string]hiero.AccountID),
	}
}

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

func (m *nodeHealthMonitor) MarkHealthy(node hiero.AccountID) {
	m.mutex.Lock()
	defer m.mutex.Unlock()
	delete(m.unhealthyNodes, node.String())
	log.Infof("Marked node %s as healthy", node)
}

func (m *nodeHealthMonitor) MarkUnhealthy(node hiero.AccountID) {
	m.mutex.Lock()
	defer m.mutex.Unlock()
	m.unhealthyNodes[node.String()] = node
	log.Warnf("Marked node %s as unhealthy", node)
}

func (m *nodeHealthMonitor) discoverUnhealthyNodes() {
	if m.client == nil {
		return
	}

	network := m.client.GetNetwork()
	if len(network) == 0 {
		return
	}

	healthyNodeIDs, err := m.freeze(m.client)
	if err != nil {
		log.Warnf("Failed to freeze transaction for healthy node discovery: %s", err)
		return
	}

	healthyMap := make(map[string]struct{}, len(healthyNodeIDs))
	for _, id := range healthyNodeIDs {
		healthyMap[id.String()] = struct{}{}
	}

	networkMap := make(map[string]struct{})
	m.mutex.Lock()
	defer m.mutex.Unlock()

	for _, nodeID := range network {
		str := nodeID.String()
		networkMap[str] = struct{}{}
		if _, ok := healthyMap[str]; !ok {
			if _, alreadyUnhealthy := m.unhealthyNodes[str]; !alreadyUnhealthy {
				m.unhealthyNodes[str] = nodeID
				log.Infof("Discovered unhealthy node %s from SDK", str)
			}
		}
	}

	// Clean up any stale nodes that are no longer in the network address book
	for str := range m.unhealthyNodes {
		if _, inNetwork := networkMap[str]; !inNetwork {
			delete(m.unhealthyNodes, str)
		}
	}
}

func (m *nodeHealthMonitor) pingUnhealthyNodes(ctx context.Context) {
	m.discoverUnhealthyNodes()

	m.mutex.RLock()
	if len(m.unhealthyNodes) == 0 {
		m.mutex.RUnlock()
		return
	}
	nodesToPing := make([]hiero.AccountID, 0, len(m.unhealthyNodes))
	for _, nodeID := range m.unhealthyNodes {
		nodesToPing = append(nodesToPing, nodeID)
	}
	m.mutex.RUnlock()

	var wg sync.WaitGroup
	for _, nodeID := range nodesToPing {
		select {
		case <-ctx.Done():
			return
		default:
		}

		wg.Add(1)
		go func(id hiero.AccountID) {
			defer wg.Done()
			err := m.ping(id)
			if err == nil {
				m.MarkHealthy(id)
			} else {
				log.Warnf("Node %s is still unhealthy: %s", id, err)
			}
		}(nodeID)
	}
	wg.Wait()
}

func (m *nodeHealthMonitor) Probe(ctx context.Context, candidates []hiero.AccountID) (hiero.AccountID, bool) {
	if len(candidates) == 0 {
		return hiero.AccountID{}, false
	}

	timeout := m.config.Timeout
	if timeout <= 0 {
		timeout = defaultHealthCheckTimeout
	}

	probeCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	resultChan := make(chan hiero.AccountID, len(candidates))
	var wg sync.WaitGroup

	for _, nodeID := range candidates {
		wg.Add(1)
		go func(id hiero.AccountID) {
			defer wg.Done()

			done := make(chan error, 1)
			go func() {
				done <- m.ping(id)
			}()

			select {
			case <-probeCtx.Done():
				return
			case err := <-done:
				if err == nil {
					select {
					case resultChan <- id:
					default:
					}
				}
			}
		}(nodeID)
	}

	go func() {
		wg.Wait()
		close(resultChan)
	}()

	select {
	case <-probeCtx.Done():
		log.Warnf("Timeboxed emergency probe timed out after %s", timeout)
		return hiero.AccountID{}, false
	case recoveredNode, ok := <-resultChan:
		if ok {
			m.MarkHealthy(recoveredNode)
			return recoveredNode, true
		}
		return hiero.AccountID{}, false
	}
}

func (m *nodeHealthMonitor) Start(ctx context.Context) {
	frequency := m.config.Frequency
	if frequency <= 0 {
		frequency = defaultHealthCheckFrequency
	}

	go func() {
		log.Infof("Starting node health monitor with frequency %s", frequency)
		ticker := time.NewTicker(frequency)
		defer ticker.Stop()

		for {
			select {
			case <-ctx.Done():
				log.Info("Stopping node health monitor")
				return
			case <-ticker.C:
				m.pingUnhealthyNodes(ctx)
			}
		}
	}()
}
