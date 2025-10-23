// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Objects;

/**
 * Links
 */
@JsonPropertyOrder({Links.JSON_PROPERTY_NEXT})
public class Links {
    public static final String JSON_PROPERTY_NEXT = "next";

    @jakarta.annotation.Nullable
    private String next;

    public Links() {}

    public Links next(@jakarta.annotation.Nullable String next) {
        this.next = next;
        return this;
    }

    /**
     * Get next
     * @return next
     */
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_NEXT)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public String getNext() {
        return next;
    }

    @JsonProperty(JSON_PROPERTY_NEXT)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setNext(@jakarta.annotation.Nullable String next) {
        this.next = next;
    }

    /**
     * Return true if this Links object is equal to o.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Links links = (Links) o;
        return Objects.equals(this.next, links.next);
    }

    @Override
    public int hashCode() {
        return Objects.hash(next);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Links {\n");
        sb.append("    next: ").append(toIndentedString(next)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}
