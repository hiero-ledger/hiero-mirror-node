// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

interface ISlotContract {
    function readFirstHalf() external view returns (uint256);
    function readSecondHalf() external view returns (uint256);
}

contract SlotContractCaller {
    ISlotContract public slotContract;

    constructor(address _slotContract) {
        require(_slotContract != address(0), "Invalid SlotContract address");
        slotContract = ISlotContract(_slotContract);
    }

    function heavyReadBothHalves() external view returns (uint256 totalSlotsRead) {
        uint256 slots = 0;

        for (uint256 i = 0; i < 5; i++) {
            slots += slotContract.readFirstHalf();
        }

        for (uint256 i = 0; i < 5; i++) {
            slots += slotContract.readSecondHalf();
        }

        return slots;
    }
}
