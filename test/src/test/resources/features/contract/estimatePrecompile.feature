@contractbase @fullsuite @estimateprecompile @web3 @acceptance
Feature: EstimateGas Contract Base Coverage Feature

  Scenario Outline: Validate EstimateGas with precompile
    Given I create estimate precompile contract with 0 balance
    Given I create erc test contract with 0 balance
    Given I get exchange rates
    Given I successfully create Precompile contract with 0 balance
    Given I successfully create fungible tokens
    Given I successfully create non fungible tokens
    Given I mint and verify a new nft
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I verify the mirror node REST API returns contract states for deployed contract
    And I set lower deviation at 5% and upper deviation at 20%
    Then I call estimateGas with associate function for fungible token
    Then I call estimateGas with associate function for NFT
    Then I call estimateGas with dissociate token function without association for fungible token
    Then I call estimateGas with dissociate token function without association for NFT
    Then I call estimateGas with nested associate function that executes it twice for fungible token
    Then I call estimateGas with nested associate function that executes it twice for NFT
    Then I call estimate gas that associates FUNGIBLE token dissociates and fails token transfer
    Then I call estimate gas that associates NFT token dissociates and fails token transfer
    And I associate the receiver account with the fungible token
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with dissociate token function for fungible token
    And I associate the receiver account with the NFT
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with dissociate token function for NFT
    Then I call estimateGas with dissociate and associate nested function for fungible token
    Then I call estimateGas with dissociate and associate nested function for NFT
    Then I call estimateGas with approve function without association
    Then I call estimateGas with approveNFT function without association
    Then I call estimateGas with setApprovalForAll function without association
    And I associate contracts with the tokens and approve all nft serials
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with ERC approve function
    Then I call estimateGas with setApprovalForAll function
    Then I call estimateGas with transferFrom function without approval
    Then I call estimateGas with ERC transferFrom function without approval
    And I approve receiver account to use the NFT with id 1
    Then the mirror node REST API should return status 200 for the HAPI transaction
    And I approve the contract to use fungible token
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with transferFrom function with more than the approved allowance
    Then I call estimateGas with ERC transferFrom function with more than the approved allowance
    Then I call estimateGas with transferFromNFT with invalid serial number
    Then I call estimateGas with transferNFT function
    And I approve the receiver account to use fungible token and transfer fungible token to the erc contract
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with ERC transferFrom function
    Then I call estimateGas with ERC transfer function
    Then I call estimateGas with associateTokens function for fungible tokens
    Then I call estimateGas with associateTokens function for NFTs
    And I associate the fungible_kyc_unfrozen token with the receiver account
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with dissociateTokens function for fungible tokens
    And I associate the nft_kyc_unfrozen with the receiver account
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with dissociateTokens function for NFTs
    And I associate and approve the second receiver to use the fungible_kyc_unfrozen token
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with transferTokens function
    And I mint a new NFT and approve second receiver account to all serial numbers
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with transferNFTs function
    Then I call estimateGas with cryptoTransfer function for hbars
    Then I call estimateGas with cryptoTransfer function for nft
    Then I call estimateGas with cryptoTransfer function for fungible tokens
    Then I call estimateGas with burnToken function for fungible token
    Then I call estimateGas with burnToken function for NFT
    Then I call estimateGas with CreateFungibleToken function
    Then I call estimateGas with CreateNFT function
    Then I call estimateGas with CreateFungibleToken function with custom fees
    Then I call estimateGas with CreateNFT function with custom fees
    And I approve and transfer fungible tokens to receiver account
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with WipeTokenAccount function
    Then I call estimateGas with WipeTokenAccount function with invalid amount
    And I transfer NFT to receiver account
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with WipeNFTAccount function
    Then I call estimateGas with WipeNFTAccount function with invalid serial number
    Then I call estimateGas with GrantKYC function for fungible token
    Then I call estimateGas with GrantKYC function for NFT
    Then I call estimateGas with RevokeTokenKYC function for fungible token
    Then I call estimateGas with RevokeTokenKYC function for NFT
    Then I call estimateGas with Grant and Revoke KYC nested function
    Then I call estimateGas with Freeze function for fungible token
    Then I call estimateGas with Freeze function for NFT
    Then I call estimateGas with Unfreeze function for fungible token
    Then I call estimateGas with Unfreeze function for NFT
    Then I call estimateGas with nested Freeze and Unfreeze function for fungible token
    Then I call estimateGas with nested Freeze and Unfreeze function for NFT
    Then I call estimateGas with delete function for Fungible token
    Then I call estimateGas with delete function for NFT
    Then I call estimateGas with delete function for invalid token address
    Then I call estimateGas with updateTokenExpiryInfo function
    Then I call estimateGas with updateTokenInfo function
    Then I call estimateGas with updateTokenKeys function
    Then I call estimateGas with pause function for fungible token
    Then I call estimateGas with pause function for NFT
    Then I call estimateGas with unpause function for fungible token
    Then I call estimateGas with unpause function for NFT
    Then I call estimateGas for nested pause and unpause function
    Then I call estimateGas for nested pause, unpause NFT function
    Then I call estimateGas with getTokenExpiryInfo function
    Then I call estimateGas with isToken function
    Then I call estimateGas with getTokenKey function for supply
    Then I call estimateGas with getTokenKey function for KYC
    Then I call estimateGas with getTokenKey function for freeze
    Then I call estimateGas with getTokenKey function for admin
    Then I call estimateGas with getTokenKey function for wipe
    Then I call estimateGas with getTokenKey function for fee
    Then I call estimateGas with getTokenKey function for pause
    Then I call estimateGas with ERC allowance function for fungible token
    Then I call estimateGas with getApproved function for NFT
    Then I call estimateGas with ERC getApproved function for NFT
    Then I call estimateGas with isApprovedForAll function
    Then I call estimateGas with ERC isApprovedForAll function
    Then I call estimateGas with name function for fungible token
    Then I call estimateGas with name function for NFT
    Then I call estimateGas with symbol function for fungible token
    Then I call estimateGas with symbol function for NFT
    Then I call estimateGas with decimals function for fungible token
    Then I call estimateGas with totalSupply function for fungible token
    Then I call estimateGas with totalSupply function for NFT
    Then I call estimateGas with ownerOf function for NFT
    Then I call estimateGas with tokenURI function for NFT
    Then I call estimateGas with getFungibleTokenInfo function
    Then I call estimateGas with getNonFungibleTokenInfo function
    Then I call estimateGas with getTokenInfo function for fungible
    Then I call estimateGas with getTokenInfo function for NFT
    Then I call estimateGas with getTokenDefaultFreezeStatus function for fungible token
    Then I call estimateGas with getTokenDefaultFreezeStatus function for NFT
    Then I call estimateGas with getTokenDefaultKycStatus function for fungible token
    Then I call estimateGas with getTokenDefaultKycStatus function for NFT
    Then I call estimateGas with isKyc function for fungible token
    Then I call estimateGas with isKyc function for NFT
    Then I call estimateGas with isFrozen function for fungible token
    Then I call estimateGas with isFrozen function for NFT
    Then I call estimateGas with getTokenType function for fungible token
    Then I call estimateGas with getTokenType function for NFT
    Then I call estimateGas with redirect balanceOf function
    Then I call estimateGas with redirect name function
    Then I call estimateGas with redirect name function for NFT
    Then I call estimateGas with redirect symbol function
    Then I call estimateGas with redirect symbol function for NFT
    Then I call estimateGas with redirect decimals function
    Then I call estimateGas with redirect allowance function
    Then I call estimateGas with redirect getOwnerOf function
    Then I call estimateGas with redirect tokenURI function
    Then I call estimateGas with redirect isApprovedForAll function
    And I transfer fungible token to the precompile contract
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with redirect transfer function
    Then I call estimateGas with redirect transferFrom function
    Then I call estimateGas with redirect approve function
    And I approve for all nft for precompile contract
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with redirect transferFrom NFT function
    Then I call estimateGas with redirect setApprovalForAll function
    Then I call estimateGas with exchange rate tinycents to tinybars
    Then I call estimateGas with exchange rate tinybars to tinycents
    Then I call estimate gas that mints FUNGIBLE token and gets the total supply and balance
    Then I call estimate gas that mints NFT token and gets the total supply and balance
    Then I call estimate gas that burns FUNGIBLE token and gets the total supply and balance
    Then I call estimate gas that wipes FUNGIBLE token and gets the total supply and balance
    Then I call estimate gas that wipes NFT token and gets the total supply and balance
    Then I call estimate gas that pauses FUNGIBLE token, unpauses and gets the token status
    Then I call estimate gas that pauses NFT token, unpauses and gets the token status
    Then I call estimate gas that freezes FUNGIBLE token, unfreezes and gets freeze status
    Then I call estimate gas that freezes NFT token, unfreezes and gets freeze status
    Then I call estimate gas that approves FUNGIBLE token and gets allowance
    Then I call estimate gas that approves NFT token and gets allowance
    Then I call estimateGas with pseudo random seed
    Then I call estimateGas with pseudo random number

    #### These tests below are recommended to be kept at the end as they change the state #####
#    Then I call estimateGas with balanceOf function for "FUNGIBLE" and verify the estimated gas against HAPI
#    Then I call estimateGas with balanceOf function for "NFT" and verify the estimated gas against HAPI
    And I update the account and token keys
    Then the mirror node REST API should return status 200 for the HAPI transaction
#    Then I call estimateGas with transferToken function and verify the estimated gas against HAPI
    And I associate the contract with the receiver account
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimateGas with transferNFT function and verify the estimated gas against HAPI
    Then I call estimateGas with mintToken function for fungible token and verify the estimated gas against HAPI
    Then I call estimateGas with mintToken function for NFT and verify the estimated gas against HAPI
    And I approve the receiver to use the token
    Then the mirror node REST API should return status 200 for the HAPI transaction
#    Then I call estimateGas with allowance function for fungible token and verify the estimated gas against HAPI
#    Then I call estimateGas with allowance function for NFT and verify the estimated gas against HAPI
    Then I call estimateGas with approve function and verify the estimated gas against HAPI
    Then I call estimateGas with approveNFT function and verify the estimated gas against HAPI
    Then I call estimateGas with transferFrom function with fungible and verify the estimated gas against HAPI
    Then I call estimateGas with transferFromNFT function and verify the estimated gas against HAPI
    Then I call estimate gas that approves a FUNGIBLE token and transfers it
    And I approve and transfer NFT tokens to the precompile contract
    Then the mirror node REST API should return status 200 for the HAPI transaction
    Then I call estimate gas that approves a NFT token and transfers it

