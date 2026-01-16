# Hedera Mirror Node Sample Smart Contract Project

This project demonstrates a basic smart contract use case using the [Hardhat](https://hardhat.org/) ethereum development
environment. It comes with some sample contracts, test files for those contracts, a sample script that deploys that
contract, and an example of a task implementation which simply lists the available accounts.

Try running some of the following tasks:

```shell
cd src/test/resources/solidity
npx hardhat accounts
npx hardhat compile
npx hardhat clean
npx hardhat test
npx hardhat node
node scripts/sample-script.js
npx hardhat help
```

## Contract Management

The Solidity contract utilized by the acceptance tests is defined in `src/test/resources/solidity/contracts/Parent.sol`.
This one Solidity file defines both a parent and a child contract. Compiling `Parent.sol` results in two important
artifacts that are committed to the repo whenever the contract changes.

```shell
artifacts/contracts/Parent.sol/Parent.json
artifacts/contracts/Parent.sol/Child.json
```

The JSON files contain both the contract EVM bytecode and ABI for each contract. However, the parent contract references
the child contract, and thus the bytecode within `Parent.json` includes the bytecode for the child contract. Therefore,
only the parent contract need be deployed for either the Node based unit tests or the contract acceptance tests. In the
former case, the `Child.json` file is used to understand the child contract ABI so that its `vacateAddress()` function
can be invoked during the test.

**WARNING:** Contract recompilation is not automatic in the current test build process. If you make changes to `Parent.sol`,
use `npx hardhat test` to recompile and run the unit tests. The contract JSON artifacts have only been changed locally.
This will allow you to run the acceptance tests locally as well, but you must commit the two artifacts in order for
anyone else to benefit from your changes.
