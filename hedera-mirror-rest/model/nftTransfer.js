// SPDX-License-Identifier: Apache-2.0

class NftTransfer {
  /**
   * Parses nft_transfer from element in transaction.nft_transfer jsonb column
   */
  constructor(nftTransfer) {
    this.isApproval = nftTransfer.is_approval;
    this.receiverAccountId = nftTransfer.receiver_account_id;
    this.senderAccountId = nftTransfer.sender_account_id;
    this.serialNumber = nftTransfer.serial_number;
    this.tokenId = nftTransfer.token_id;
  }

  static IS_APPROVAL = `is_approval`;
  static RECEIVER_ACCOUNT_ID = `receiver_account_id`;
  static SENDER_ACCOUNT_ID = `sender_account_id`;
  static SERIAL_NUMBER = `serial_number`;
  static TOKEN_ID = `token_id`;
}

export default NftTransfer;
