// SPDX-License-Identifier: Apache-2.0

package services

import (
	"context"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/domain/types"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/errors"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/interfaces"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/persistence"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/tools"
)

// networkAPIService implements the server.NetworkAPIServicer interface.
type networkAPIService struct {
	BaseService
	addressBookEntryRepo interfaces.AddressBookEntryRepository
	network              *rTypes.NetworkIdentifier
	operationTypes       []string
	version              *rTypes.Version
}

// NetworkList implements the /network/list endpoint.
func (n *networkAPIService) NetworkList(
	_ context.Context,
	_ *rTypes.MetadataRequest,
) (*rTypes.NetworkListResponse, *rTypes.Error) {
	return &rTypes.NetworkListResponse{NetworkIdentifiers: []*rTypes.NetworkIdentifier{n.network}}, nil
}

// NetworkOptions implements the /network/options endpoint.
func (n *networkAPIService) NetworkOptions(
	_ context.Context,
	_ *rTypes.NetworkRequest,
) (*rTypes.NetworkOptionsResponse, *rTypes.Error) {
	operationStatuses := make([]*rTypes.OperationStatus, 0, len(types.GetTransactionResults()))
	for value, name := range types.GetTransactionResults() {
		operationStatuses = append(operationStatuses, &rTypes.OperationStatus{
			Status:     name,
			Successful: persistence.IsTransactionResultSuccessful(value),
		})
	}

	return &rTypes.NetworkOptionsResponse{
		Version: n.version,
		Allow: &rTypes.Allow{
			OperationStatuses:       operationStatuses,
			OperationTypes:          n.operationTypes,
			Errors:                  errors.Errors,
			HistoricalBalanceLookup: true,
		},
	}, nil
}

// NetworkStatus implements the /network/status endpoint.
func (n *networkAPIService) NetworkStatus(
	ctx context.Context,
	_ *rTypes.NetworkRequest,
) (*rTypes.NetworkStatusResponse, *rTypes.Error) {
	if !n.IsOnline() {
		return nil, errors.ErrEndpointNotSupportedInOfflineMode
	}

	genesisBlock, err := n.RetrieveGenesis(ctx)
	if err != nil {
		return nil, err
	}

	currentBlock, err := n.RetrieveLatest(ctx)
	if err != nil {
		return nil, err
	}

	peers, err := n.addressBookEntryRepo.Entries(ctx)
	if err != nil {
		return nil, err
	}

	return &rTypes.NetworkStatusResponse{
		CurrentBlockIdentifier: currentBlock.GetRosettaBlockIdentifier(),
		CurrentBlockTimestamp:  currentBlock.GetTimestampMillis(),
		GenesisBlockIdentifier: genesisBlock.GetRosettaBlockIdentifier(),
		Peers:                  peers.ToRosetta(),
	}, nil
}

// NewNetworkAPIService creates a networkAPIService instance.
func NewNetworkAPIService(
	baseService BaseService,
	addressBookEntryRepo interfaces.AddressBookEntryRepository,
	network *rTypes.NetworkIdentifier,
	version *rTypes.Version,
) server.NetworkAPIServicer {
	operationTypes := tools.GetStringValuesFromInt32StringMap(types.GetTransactionTypes())
	operationTypes = append(operationTypes, types.OperationTypeFee)
	return &networkAPIService{
		BaseService:          baseService,
		addressBookEntryRepo: addressBookEntryRepo,
		operationTypes:       operationTypes,
		network:              network,
		version:              version,
	}
}
