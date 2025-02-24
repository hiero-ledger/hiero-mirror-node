// SPDX-License-Identifier: Apache-2.0

/**
 * Network address book service endpoint view model
 */
class AddressBookServiceEndpointViewModel {
  /**
   * Constructs address book service endpoint view model
   *
   * @param {AddressBookServiceEndpoint} serviceEndpoint
   */
  constructor(serviceEndpoint) {
    this.domain_name = serviceEndpoint.domainName;
    this.ip_address_v4 = serviceEndpoint.ipAddressV4;
    this.port = serviceEndpoint.port;
  }
}

export default AddressBookServiceEndpointViewModel;
