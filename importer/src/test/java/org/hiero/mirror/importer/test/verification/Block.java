// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Objects;

/**
 * Block
 */
@JsonPropertyOrder({
    Block.JSON_PROPERTY_COUNT,
    Block.JSON_PROPERTY_GAS_USED,
    Block.JSON_PROPERTY_HAPI_VERSION,
    Block.JSON_PROPERTY_HASH,
    Block.JSON_PROPERTY_LOGS_BLOOM,
    Block.JSON_PROPERTY_NAME,
    Block.JSON_PROPERTY_NUMBER,
    Block.JSON_PROPERTY_PREVIOUS_HASH,
    Block.JSON_PROPERTY_SIZE,
    Block.JSON_PROPERTY_TIMESTAMP
})
public class Block {
    public static final String JSON_PROPERTY_COUNT = "count";

    @jakarta.annotation.Nullable
    private Integer count;

    public static final String JSON_PROPERTY_GAS_USED = "gas_used";

    @jakarta.annotation.Nullable
    private Long gasUsed;

    public static final String JSON_PROPERTY_HAPI_VERSION = "hapi_version";

    @jakarta.annotation.Nullable
    private String hapiVersion;

    public static final String JSON_PROPERTY_HASH = "hash";

    @jakarta.annotation.Nullable
    private String hash;

    public static final String JSON_PROPERTY_LOGS_BLOOM = "logs_bloom";

    @jakarta.annotation.Nullable
    private String logsBloom;

    public static final String JSON_PROPERTY_NAME = "name";

    @jakarta.annotation.Nullable
    private String name;

    public static final String JSON_PROPERTY_NUMBER = "number";

    @jakarta.annotation.Nullable
    private Integer number;

    public static final String JSON_PROPERTY_PREVIOUS_HASH = "previous_hash";

    @jakarta.annotation.Nullable
    private String previousHash;

    public static final String JSON_PROPERTY_SIZE = "size";

    @jakarta.annotation.Nullable
    private Integer size;

    public static final String JSON_PROPERTY_TIMESTAMP = "timestamp";

    @jakarta.annotation.Nullable
    private TimestampRange timestamp;

    public Block() {}

    public Block count(@jakarta.annotation.Nullable Integer count) {
        this.count = count;
        return this;
    }

    /**
     * Get count
     * minimum: 0
     * @return count
     */
    @jakarta.annotation.Nullable
    @Min(0)
    @JsonProperty(JSON_PROPERTY_COUNT)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public Integer getCount() {
        return count;
    }

    @JsonProperty(JSON_PROPERTY_COUNT)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setCount(@jakarta.annotation.Nullable Integer count) {
        this.count = count;
    }

    public Block gasUsed(@jakarta.annotation.Nullable Long gasUsed) {
        this.gasUsed = gasUsed;
        return this;
    }

    /**
     * Get gasUsed
     * minimum: 0
     * @return gasUsed
     */
    @jakarta.annotation.Nullable
    @Min(0L)
    @JsonProperty(JSON_PROPERTY_GAS_USED)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public Long getGasUsed() {
        return gasUsed;
    }

    @JsonProperty(JSON_PROPERTY_GAS_USED)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setGasUsed(@jakarta.annotation.Nullable Long gasUsed) {
        this.gasUsed = gasUsed;
    }

    public Block hapiVersion(@jakarta.annotation.Nullable String hapiVersion) {
        this.hapiVersion = hapiVersion;
        return this;
    }

    /**
     * Get hapiVersion
     * @return hapiVersion
     */
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_HAPI_VERSION)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public String getHapiVersion() {
        return hapiVersion;
    }

    @JsonProperty(JSON_PROPERTY_HAPI_VERSION)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setHapiVersion(@jakarta.annotation.Nullable String hapiVersion) {
        this.hapiVersion = hapiVersion;
    }

    public Block hash(@jakarta.annotation.Nullable String hash) {
        this.hash = hash;
        return this;
    }

    /**
     * Get hash
     * @return hash
     */
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_HASH)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public String getHash() {
        return hash;
    }

    @JsonProperty(JSON_PROPERTY_HASH)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setHash(@jakarta.annotation.Nullable String hash) {
        this.hash = hash;
    }

    public Block logsBloom(@jakarta.annotation.Nullable String logsBloom) {
        this.logsBloom = logsBloom;
        return this;
    }

    /**
     * A hex encoded 256-byte array with 0x prefix
     * @return logsBloom
     */
    @jakarta.annotation.Nullable
    @Pattern(regexp = "^0x[0-9a-fA-F]{512}$")
    @JsonProperty(JSON_PROPERTY_LOGS_BLOOM)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public String getLogsBloom() {
        return logsBloom;
    }

    @JsonProperty(JSON_PROPERTY_LOGS_BLOOM)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setLogsBloom(@jakarta.annotation.Nullable String logsBloom) {
        this.logsBloom = logsBloom;
    }

    public Block name(@jakarta.annotation.Nullable String name) {
        this.name = name;
        return this;
    }

    /**
     * Get name
     * @return name
     */
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_NAME)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public String getName() {
        return name;
    }

    @JsonProperty(JSON_PROPERTY_NAME)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setName(@jakarta.annotation.Nullable String name) {
        this.name = name;
    }

    public Block number(@jakarta.annotation.Nullable Integer number) {
        this.number = number;
        return this;
    }

    /**
     * Get number
     * minimum: 0
     * @return number
     */
    @jakarta.annotation.Nullable
    @Min(0)
    @JsonProperty(JSON_PROPERTY_NUMBER)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public Integer getNumber() {
        return number;
    }

    @JsonProperty(JSON_PROPERTY_NUMBER)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setNumber(@jakarta.annotation.Nullable Integer number) {
        this.number = number;
    }

    public Block previousHash(@jakarta.annotation.Nullable String previousHash) {
        this.previousHash = previousHash;
        return this;
    }

    /**
     * Get previousHash
     * @return previousHash
     */
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_PREVIOUS_HASH)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public String getPreviousHash() {
        return previousHash;
    }

    @JsonProperty(JSON_PROPERTY_PREVIOUS_HASH)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setPreviousHash(@jakarta.annotation.Nullable String previousHash) {
        this.previousHash = previousHash;
    }

    public Block size(@jakarta.annotation.Nullable Integer size) {
        this.size = size;
        return this;
    }

    /**
     * Get size
     * @return size
     */
    @jakarta.annotation.Nullable
    @JsonProperty(JSON_PROPERTY_SIZE)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public Integer getSize() {
        return size;
    }

    @JsonProperty(JSON_PROPERTY_SIZE)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setSize(@jakarta.annotation.Nullable Integer size) {
        this.size = size;
    }

    public Block timestamp(@jakarta.annotation.Nullable TimestampRange timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    /**
     * Get timestamp
     * @return timestamp
     */
    @jakarta.annotation.Nullable
    @Valid
    @JsonProperty(JSON_PROPERTY_TIMESTAMP)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public TimestampRange getTimestamp() {
        return timestamp;
    }

    @JsonProperty(JSON_PROPERTY_TIMESTAMP)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setTimestamp(@jakarta.annotation.Nullable TimestampRange timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Return true if this Block object is equal to o.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Block block = (Block) o;
        return Objects.equals(this.count, block.count)
                && Objects.equals(this.gasUsed, block.gasUsed)
                && Objects.equals(this.hapiVersion, block.hapiVersion)
                && Objects.equals(this.hash, block.hash)
                && Objects.equals(this.logsBloom, block.logsBloom)
                && Objects.equals(this.name, block.name)
                && Objects.equals(this.number, block.number)
                && Objects.equals(this.previousHash, block.previousHash)
                && Objects.equals(this.size, block.size)
                && Objects.equals(this.timestamp, block.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, gasUsed, hapiVersion, hash, logsBloom, name, number, previousHash, size, timestamp);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Block {\n");
        sb.append("    count: ").append(toIndentedString(count)).append("\n");
        sb.append("    gasUsed: ").append(toIndentedString(gasUsed)).append("\n");
        sb.append("    hapiVersion: ").append(toIndentedString(hapiVersion)).append("\n");
        sb.append("    hash: ").append(toIndentedString(hash)).append("\n");
        sb.append("    logsBloom: ").append(toIndentedString(logsBloom)).append("\n");
        sb.append("    name: ").append(toIndentedString(name)).append("\n");
        sb.append("    number: ").append(toIndentedString(number)).append("\n");
        sb.append("    previousHash: ").append(toIndentedString(previousHash)).append("\n");
        sb.append("    size: ").append(toIndentedString(size)).append("\n");
        sb.append("    timestamp: ").append(toIndentedString(timestamp)).append("\n");
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
