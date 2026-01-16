// SPDX-License-Identifier: Apache-2.0

class OrderSpec {
  /**
   * Creates an OrderSpec object
   * @param {string} column
   * @param {'asc'|'desc'} order
   */
  constructor(column, order) {
    this.column = column;
    this.order = order;
  }

  toString() {
    return `${this.column} ${this.order}`;
  }

  static from(column, order) {
    return new OrderSpec(column, order);
  }
}

export default OrderSpec;
