// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;

contract ExchangeRatePrecompileHistorical {

    uint256 constant TINY_PARTS_PER_WHOLE = 100_000_000;
    address constant PRECOMPILE_ADDRESS = address(0x168);
    bytes4 constant TINYCENTS_TO_TINYBARS = bytes4(keccak256("tinycentsToTinybars(uint256)"));
    bytes4 constant TINYBARS_TO_TINYCENTS = bytes4(keccak256("tinybarsToTinycents(uint256)"));

    function tinycentsToTinybars(uint256 tinycents) external payable returns (uint256 tinybars) {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.call{value: msg.value}(
            abi.encodeWithSelector(TINYCENTS_TO_TINYBARS, tinycents));
        require(success);
        tinybars = abi.decode(result, (uint256));
    }

    function tinybarsToTinycents(uint256 tinybars) external payable returns (uint256 tinycents) {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.call{value: msg.value}(
            abi.encodeWithSelector(TINYBARS_TO_TINYCENTS, tinybars));
        require(success);
        tinycents = abi.decode(result, (uint256));
    }
}