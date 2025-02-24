// SPDX-License-Identifier: Apache-2.0

class TokenAccount {
  static TOKEN_ID = `token_id`;
  static ACCOUNT_ID = `account_id`;

  /**
   * Parses token_account table columns into object
   */
  constructor(tokenRelationship) {
    this.automaticAssociation = tokenRelationship.automatic_association;
    this.balance = tokenRelationship.balance;
    this.createdTimestamp = tokenRelationship.created_timestamp;
    this.decimals = tokenRelationship.decimals ?? null;
    this.freezeStatus = tokenRelationship.freeze_status;
    this.kycStatus = tokenRelationship.kyc_status;
    this.tokenId = tokenRelationship.token_id;
  }

  static tableAlias = 'ta';
  static tableName = 'token_account';

  static AUTOMATIC_ASSOCIATION = 'automatic_association';
  static ASSOCIATED = 'associated';
  static BALANCE = 'balance';
  static CREATED_TIMESTAMP = 'created_timestamp';
  static FREEZE_STATUS = `freeze_status`;
  static KYC_STATUS = 'kyc_status';

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }
}

export default TokenAccount;
