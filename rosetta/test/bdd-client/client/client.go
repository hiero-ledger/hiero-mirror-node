// SPDX-License-Identifier: Apache-2.0

package client

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"time"

	rosettaAsserter "github.com/coinbase/rosetta-sdk-go/asserter"
	rosettaClient "github.com/coinbase/rosetta-sdk-go/client"
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
)

const agent = "rosetta-test-bdd-client"

var defaultInitialBalance = hiero.NewHbar(10)

type Client struct {
	sdkClient     *hiero.Client
	network       *types.NetworkIdentifier
	offlineClient *rosettaClient.APIClient
	onlineClient  *rosettaClient.APIClient
	operators     []Operator
	privateKeys   map[hiero.AccountID]hiero.PrivateKey
	dataRetry     retry
	submitRetry   retry
}

func (c Client) CreateAccount(initialBalance hiero.Hbar) (*hiero.AccountID, *hiero.PrivateKey, error) {
	if initialBalance.AsTinybar() == 0 {
		initialBalance = defaultInitialBalance
	}

	sk, err := hiero.PrivateKeyGenerateEd25519()
	if err != nil {
		log.Errorf("Failed to generate private key for new account: %s", err)
		return nil, nil, err
	}

	resp, err := hiero.NewAccountCreateTransaction().
		SetInitialBalance(initialBalance).
		SetKeyWithoutAlias(sk.PublicKey()).
		Execute(c.sdkClient)
	if err != nil {
		log.Errorf("Failed to execute AccountCreate transaction: %s", err)
		return nil, nil, err
	}

	receipt, err := resp.GetReceipt(c.sdkClient)
	if err != nil {
		log.Errorf("Failed to get receipt for AccountCreate transaction: %s", err)
		return nil, nil, err
	}

	log.Infof("Successfully created new account %s with initial balance %s", receipt.AccountID, initialBalance)
	return receipt.AccountID, &sk, nil
}

func (c Client) DeleteAccount(accountId hiero.AccountID, privateKey *hiero.PrivateKey) error {
	_, err := hiero.NewAccountDeleteTransaction().
		SetAccountID(accountId).
		SetTransferAccountID(c.operators[0].Id).
		SetTransactionID(hiero.TransactionIDGenerate(accountId)).
		Sign(*privateKey).
		Execute(c.sdkClient)
	if err != nil {
		log.Errorf("Failed to delete account %s: %v", accountId, err)
		return err
	}

	log.Infof("Successfully submitted the AccountDeleteTransaction for %s", accountId)
	return nil
}

func (c Client) GetAccountBalance(ctx context.Context, account *types.AccountIdentifier) (
	*types.AccountBalanceResponse,
	error,
) {
	request := &types.AccountBalanceRequest{NetworkIdentifier: c.network, AccountIdentifier: account}
	response, rErr, err := c.onlineClient.AccountAPI.AccountBalance(ctx, request)
	return response, c.handleError(fmt.Sprintf("Failed to get balance for %s", account.Address), rErr, err)
}

func (c Client) FindTransaction(ctx context.Context, hash string) (*types.Transaction, error) {
	blockApi := c.onlineClient.BlockAPI

	status, rosettaErr, err := c.onlineClient.NetworkAPI.NetworkStatus(
		ctx,
		&types.NetworkRequest{NetworkIdentifier: c.network},
	)
	if err = c.handleError("Failed to get network status", rosettaErr, err); err != nil {
		return nil, err
	}

	blockIndex := status.CurrentBlockIdentifier.Index
	log.Debugf("Current block index %d", blockIndex)

	var transaction *types.Transaction
	tryFindTransaction := func() (bool, *types.Error, error) {
		log.Infof("Looking for transaction %s in block %d", hash, blockIndex)
		blockRequest := &types.BlockRequest{
			NetworkIdentifier: c.network,
			BlockIdentifier:   &types.PartialBlockIdentifier{Index: &blockIndex},
		}
		blockResponse, rosettaErr, err := blockApi.Block(ctx, blockRequest)
		if rosettaErr != nil || err != nil {
			return false, rosettaErr, err
		}

		log.Infof("Got block response for block %d", blockIndex)
		for _, tx := range blockResponse.Block.Transactions {
			if tx.TransactionIdentifier.Hash == hash {
				log.Infof("Found transaction %s in block %d", hash, blockIndex)
				transaction = tx
				return true, nil, nil
			}
		}

		for _, txId := range blockResponse.OtherTransactions {
			if txId.Hash == hash {
				log.Infof("Found transaction %s in block %d other transactions list", hash, blockIndex)
				blockTransactionRequest := &types.BlockTransactionRequest{
					NetworkIdentifier:     c.network,
					BlockIdentifier:       blockResponse.Block.BlockIdentifier,
					TransactionIdentifier: txId,
				}
				blockTransactionResponse, rosettaErr, err := blockApi.BlockTransaction(ctx, blockTransactionRequest)
				if rosettaErr != nil || err != nil {
					return false, rosettaErr, err
				}
				transaction = blockTransactionResponse.Transaction
				return true, nil, nil
			}
		}

		// only increase blockIndex when the block is successfully retrieved
		log.Infof("Transaction %s not found in block %d", hash, blockIndex)
		blockIndex += 1

		return false, nil, nil
	}

	rosettaErr, err = c.dataRetry.Run(tryFindTransaction, false)
	if err = c.handleError(fmt.Sprintf("Failed to find transaction %s", hash), rosettaErr, err); err != nil {
		return nil, err
	}

	return transaction, nil
}

func (c Client) GetOperator(index int) Operator {
	return c.operators[index]
}

// Submit submits the operations to the network, goes through the construction preprocess, metadata, payloads, combine,
// and submit workflow. Note payloads signing happens between payloads and combine.
func (c Client) Submit(
	ctx context.Context,
	memo string,
	operations []*types.Operation,
	signers map[string]hiero.PrivateKey,
) (string, error) {
	var txHash string

	offlineConstructor := c.offlineClient.ConstructionAPI
	onlineConstructor := c.onlineClient.ConstructionAPI

	if signers == nil {
		operator := c.operators[0]
		signers = map[string]hiero.PrivateKey{operator.Id.String(): operator.PrivateKey}
	} else {
		for signerId := range signers {
			if strings.HasPrefix(signerId, "0x") {
				continue
			}

			accountId, _ := hiero.AccountIDFromString(signerId)
			operatorKey, ok := c.privateKeys[accountId]
			if ok {
				signers[signerId] = operatorKey
			}

			if len(signers[signerId].Bytes()) == 0 {
				return txHash, errors.Errorf("No private key for signer %s", signers)
			}
		}
	}

	trySubmit := func() (bool, *types.Error, error) {
		// preprocess
		metadata := make(map[string]interface{})
		if len(memo) != 0 {
			metadata["memo"] = memo
		}
		preprocessRequest := &types.ConstructionPreprocessRequest{
			NetworkIdentifier: c.network,
			Metadata:          metadata,
			Operations:        operations,
		}
		preprocessResponse, rosettaErr, err := offlineConstructor.ConstructionPreprocess(ctx, preprocessRequest)
		if err1 := c.handleError("Failed to handle preprocess request", rosettaErr, err); err1 != nil {
			return false, rosettaErr, err
		}

		// metadata, note currently /construction/metadata doesn't return any chain-specific data
		metadataRequest := &types.ConstructionMetadataRequest{
			NetworkIdentifier: c.network,
			Options:           preprocessResponse.Options,
		}
		metadataResponse, rosettaErr, err := onlineConstructor.ConstructionMetadata(ctx, metadataRequest)
		if err1 := c.handleError("Failed to handle metadata request", rosettaErr, err); err1 != nil {
			return false, rosettaErr, err
		}

		// payloads
		payloadsRequest := &types.ConstructionPayloadsRequest{
			NetworkIdentifier: c.network,
			Operations:        operations,
			Metadata:          metadataResponse.Metadata,
		}
		payloadsResponse, rosettaErr, err := offlineConstructor.ConstructionPayloads(ctx, payloadsRequest)
		if err1 := c.handleError("Failed to handle payloads request", rosettaErr, err); err1 != nil {
			return false, rosettaErr, err
		}

		// sign
		signatures := make([]*types.Signature, 0)
		for _, signingPayload := range payloadsResponse.Payloads {
			signerAddress := signingPayload.AccountIdentifier.Address
			key := signers[signerAddress]
			signature := key.Sign(signingPayload.Bytes)
			signatures = append(signatures, &types.Signature{
				SigningPayload: signingPayload,
				PublicKey: &types.PublicKey{
					Bytes:     key.PublicKey().Bytes(),
					CurveType: types.Edwards25519,
				},
				SignatureType: types.Ed25519,
				Bytes:         signature,
			})
		}

		// combine
		combineRequest := &types.ConstructionCombineRequest{
			NetworkIdentifier:   c.network,
			UnsignedTransaction: payloadsResponse.UnsignedTransaction,
			Signatures:          signatures,
		}
		combineResponse, rosettaErr, err := offlineConstructor.ConstructionCombine(ctx, combineRequest)
		if err1 := c.handleError("Failed to handle combine request", rosettaErr, err); err1 != nil {
			return false, rosettaErr, err
		}

		// submit
		submitRequest := &types.ConstructionSubmitRequest{
			NetworkIdentifier: c.network,
			SignedTransaction: combineResponse.SignedTransaction,
		}
		submitResponse, rosettaErr, err := onlineConstructor.ConstructionSubmit(ctx, submitRequest)
		if err1 := c.handleError("Failed to handle submit request", rosettaErr, err); err1 != nil {
			return false, rosettaErr, err
		}

		txHash = submitResponse.TransactionIdentifier.Hash
		return true, nil, nil
	}

	rosettaErr, err := c.submitRetry.Run(trySubmit, true)
	return txHash, c.handleError("Submit failed", rosettaErr, err)
}

func (c Client) handleError(message string, rosettaError *types.Error, err error) error {
	if rosettaError != nil {
		err = errors.Errorf("%s: %+v", message, rosettaError)
		log.Error(err)
		return err
	}
	if err != nil {
		log.Errorf("%s: %v", message, err)
		return err
	}
	return nil
}

func createRosettaClient(serverUrl string, timeout time.Duration) *rosettaClient.APIClient {
	cfg := rosettaClient.NewConfiguration(serverUrl, agent, &http.Client{Timeout: timeout})
	return rosettaClient.NewAPIClient(cfg)
}

func NewClient(serverCfg Server, operators []Operator) Client {
	offlineClient := createRosettaClient(serverCfg.OfflineUrl, serverCfg.HttpTimeout)
	onlineClient := createRosettaClient(serverCfg.OnlineUrl, serverCfg.HttpTimeout)

	ctx := context.Background()
	networkList, rosettaErr, err := onlineClient.NetworkAPI.NetworkList(ctx, &types.MetadataRequest{})
	if rosettaErr != nil {
		log.Fatalf("Failed to list network: %+v", rosettaErr)
	}
	if err != nil {
		log.Fatal(err)
	}

	network := networkList.NetworkIdentifiers[0]
	var subNetwork string
	if network.SubNetworkIdentifier != nil {
		subNetwork = network.SubNetworkIdentifier.Network
	}
	log.Infof("Network: %s, SubNetwork: %s", network.Network, subNetwork)

	networkRequest := &types.NetworkRequest{NetworkIdentifier: network}
	status, rosettaErr, err := onlineClient.NetworkAPI.NetworkStatus(ctx, networkRequest)
	if rosettaErr != nil {
		log.Fatalf("Failed to get network status: %+v", rosettaErr)
	}
	if err != nil {
		log.Fatal(err)
	}

	log.Infof("Network status - current block: %+v", status.CurrentBlockIdentifier)

	options, rosettaErr, err := onlineClient.NetworkAPI.NetworkOptions(ctx, networkRequest)
	if rosettaErr != nil {
		log.Fatalf("Failed to get network options: %+v", rosettaErr)
	}
	if err != nil {
		log.Fatal(err)
	}

	log.Info("Fetched Network Options")

	if err := rosettaAsserter.NetworkOptionsResponse(options); err != nil {
		log.Fatalf("Failed to assert network options response: %v", err)
	}

	log.Info("Successfully set up rosetta clients for online and offline servers")

	privateKeys := make(map[hiero.AccountID]hiero.PrivateKey)
	for _, operator := range operators {
		privateKeys[operator.Id] = operator.PrivateKey
	}

	// create SDK client
	var sdkClient *hiero.Client
	if len(serverCfg.Network) == 0 {
		if sdkClient, err = hiero.ClientForName(network.Network); err != nil {
			log.Fatalf("Failed to create SDK client for '%s'", network.Network)
		}
		log.Infof("Successfully created SDK client for '%s'", network.Network)
	} else {
		sdkClient = hiero.ClientForNetwork(serverCfg.Network)
		log.Infof("Successfully created client for custom network '%v'", serverCfg.Network)
	}
	sdkClient.SetOperator(operators[0].Id, operators[0].PrivateKey)

	return Client{
		dataRetry:     serverCfg.DataRetry,
		sdkClient:     sdkClient,
		network:       network,
		offlineClient: offlineClient,
		onlineClient:  onlineClient,
		operators:     operators,
		privateKeys:   privateKeys,
		submitRetry:   serverCfg.SubmitRetry,
	}
}
