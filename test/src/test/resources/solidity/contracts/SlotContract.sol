// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract SlotContract {
    uint256 public constant SLOT_COUNT = 600;
    uint256 public constant HALF_SLOT_COUNT = SLOT_COUNT / 2;

    mapping(uint256 => uint256) public data;

    constructor() {
        for (uint256 i = 0; i < SLOT_COUNT; i++) {
            data[i] = i + 1;
        }
    }

    // Reads first 300 slots (0–300), returns only how many slots were read
    function readFirstHalf() external view returns (uint256 slotsRead) {
        uint256 current = 0;
        for (uint256 i = 0; i < HALF_SLOT_COUNT; i++) {
            // trigger SLOAD
            current = data[i];
        }
        return HALF_SLOT_COUNT;
    }

    // Reads second 300 slots (300–600), returns only how many slots were read
    function readSecondHalf() external view returns (uint256 slotsRead) {
        uint256 current = 0;
        for (uint256 i = HALF_SLOT_COUNT; i < SLOT_COUNT; i++) {
            // trigger SLOAD
            current = data[i];
        }
        return HALF_SLOT_COUNT;
    }
}