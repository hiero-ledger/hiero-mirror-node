package main

import (
	"context"
	"fmt"

	hiero "github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
)

func newClient(cfg config) (*hiero.Client, error) {
	var client *hiero.Client
	var err error

	switch cfg.network {
	case "testnet":
		client = hiero.ClientForTestnet()
	case "previewnet":
		client = hiero.ClientForPreviewnet()
	case "mainnet":
		client = hiero.ClientForMainnet()
	case "other":
		netmap, err := buildNetworkFromMirrorNodes(context.Background(), cfg.mirrorRest)
		if err != nil {
			return nil, err
		}
		client = hiero.ClientForNetwork(netmap)
	default:
		return nil, fmt.Errorf("unknown network %q (testnet|previewnet|mainnet|other)", cfg.network)
	}

	opID, err := hiero.AccountIDFromString(cfg.operatorID)
	if err != nil {
		return nil, fmt.Errorf("invalid operator id: %w", err)
	}

	opKey, err := hiero.PrivateKeyFromString(cfg.operatorKey)
	if err != nil {
		return nil, fmt.Errorf("invalid operator key: %w", err)
	}

	client.SetOperator(opID, opKey)
	return client, nil
}