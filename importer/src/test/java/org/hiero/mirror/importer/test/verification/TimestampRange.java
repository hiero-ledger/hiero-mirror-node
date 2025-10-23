// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Pattern;
import java.util.Objects;

/**
 * A timestamp range an entity is valid for
 */
@JsonPropertyOrder({TimestampRange.JSON_PROPERTY_FROM, TimestampRange.JSON_PROPERTY_TO})
@jakarta.annotation.Generated(
        value = "org.openapitools.codegen.languages.JavaClientCodegen",
        date = "2025-08-28T10:59:40.931742-05:00[America/Chicago]",
        comments = "Generator version: 7.14.0")
public class TimestampRange {
    public static final String JSON_PROPERTY_FROM = "from";

    @jakarta.annotation.Nullable
    private String from;

    public static final String JSON_PROPERTY_TO = "to";

    @jakarta.annotation.Nullable
    private String to;

    public TimestampRange() {}

    public TimestampRange from(@jakarta.annotation.Nullable String from) {
        this.from = from;
        return this;
    }

    /**
     * The inclusive from timestamp in seconds
     * @return from
     */
    @jakarta.annotation.Nullable
    @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,9})?$")
    @JsonProperty(JSON_PROPERTY_FROM)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public String getFrom() {
        return from;
    }

    @JsonProperty(JSON_PROPERTY_FROM)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setFrom(@jakarta.annotation.Nullable String from) {
        this.from = from;
    }

    public TimestampRange to(@jakarta.annotation.Nullable String to) {
        this.to = to;
        return this;
    }

    /**
     * The exclusive to timestamp in seconds
     * @return to
     */
    @jakarta.annotation.Nullable
    @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,9})?$")
    @JsonProperty(JSON_PROPERTY_TO)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public String getTo() {
        return to;
    }

    @JsonProperty(JSON_PROPERTY_TO)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setTo(@jakarta.annotation.Nullable String to) {
        this.to = to;
    }

    /**
     * Return true if this TimestampRange object is equal to o.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TimestampRange timestampRange = (TimestampRange) o;
        return Objects.equals(this.from, timestampRange.from) && Objects.equals(this.to, timestampRange.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class TimestampRange {\n");
        sb.append("    from: ").append(toIndentedString(from)).append("\n");
        sb.append("    to: ").append(toIndentedString(to)).append("\n");
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
