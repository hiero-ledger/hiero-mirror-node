@contractbase @fullsuite
Feature: Contract Base Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Validate Contract Flows

        Using the Java SDK and the mirror node REST API, create, update and call a smart contract and
        verify proper operation. Use that same parent contract to create a second child contract using
        the CREATE2 flow, namely by retrieving the child contract bytecode and using it, and a salt value,
        to compute the child's EVM address and then deploying the contract to that address.

        Once the child EVM address is known, issue a crypto transfer transaction to that EVM address, creating
        a hollow account. Verify the account exists and indeed is hollow, but that a contract does not yet
        exist at that EVM address. Then, using a parent contract function, deploy the child contract using
        the CREATE2 opcode and with the same bytecode and salt, thus this is done to the child EVM address.

        With the contract now present, and the account no longer hollow, invoke a function on the child contract
        to cause its self destruction, vacating the EVM address for future use. Also, Finally, delete the original parent
        contract and its underlying bytecode file.

        Given I successfully create a contract from the parent contract bytes with 10000000 balance
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the contract
        When I successfully update the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the updated contract entity
        When I successfully call the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the contract
        And I call the contract via the mirror node REST API
        Given I call the parent contract to retrieve child contract bytecode
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        When I call the parent contract evm address function with the bytecode of the child contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And I create a hollow account using CryptoTransfer of <transferAmount> to the evm address
        Then the mirror node REST API should return status <httpStatusCode> for the account transaction
        And the mirror node REST API should verify the account receiving <transferAmount> is hollow
        And the mirror node REST API should indicate not found when using evm address to retrieve as a contract
        When I create a child contract by calling parent contract function to deploy using CREATE2
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should retrieve the child contract when using evm address
        And the mirror node REST API should verify the account is no longer hollow
        And the mirror node Rest API should verify that the parent contract have correct nonce
        When I successfully delete the child contract by calling it and causing it to self destruct
        Then the mirror node REST API should return status <httpStatusCode> for the self destruct transaction
        When I successfully delete the parent contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deleted contract entity
        Then I successfully delete the parent contract bytecode file
        Examples:
            | httpStatusCode | transferAmount |
            | 200            | 1000           |
