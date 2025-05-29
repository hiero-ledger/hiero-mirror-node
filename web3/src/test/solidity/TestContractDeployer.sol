// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "./TestContract.sol";

/// @title TestContractDeployer
/// @notice Deploys multiple TestContract instances
contract TestContractDeployer {
    address public firstTestContract;
    address public secondTestContract;

    /// @notice Deploys two instances of TestContract with different constructor args
    function deployTestContracts() external {
        TestContract first = new TestContract("First Test Contract");
        TestContract second = new TestContract("Second Test Contract");

        firstTestContract = address(first);
        secondTestContract = address(second);
    }
}
