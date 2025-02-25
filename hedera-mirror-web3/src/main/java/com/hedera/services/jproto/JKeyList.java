// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.jproto;

import java.util.LinkedList;
import java.util.List;

/**
 * Maps to proto Key of type KeyList.
 */
public class JKeyList extends JKey {
    private List<JKey> keys;

    public JKeyList() {
        this.keys = new LinkedList<>();
    }

    public JKeyList(List<JKey> keys) {
        if (keys == null) {
            throw new IllegalArgumentException("JKeyList cannot be constructed with a null 'keys' argument!");
        }
        this.keys = keys;
    }

    @Override
    public String toString() {
        return "<JKeyList: keys=" + keys.toString() + ">";
    }

    @Override
    public boolean isEmpty() {
        if (keys != null) {
            for (var key : keys) {
                if ((null != key) && !key.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isValid() {
        if (isEmpty()) {
            return false;
        } else {
            for (var key : keys) {
                // if any key is null or invalid then this key list is invalid
                if ((null == key) || !key.isValid()) {
                    return false;
                }
            }
            return true;
        }
    }

    public List<JKey> getKeysList() {
        return keys;
    }

    @Override
    public JKeyList getKeyList() {
        return this;
    }
}
