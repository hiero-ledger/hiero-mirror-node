// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({BlocksResponse.JSON_PROPERTY_BLOCKS, BlocksResponse.JSON_PROPERTY_LINKS})
public class BlocksResponse {
    public static final String JSON_PROPERTY_BLOCKS = "blocks";

    @jakarta.annotation.Nullable
    private List<@Valid Block> blocks = new ArrayList<>();

    public static final String JSON_PROPERTY_LINKS = "links";

    @jakarta.annotation.Nullable
    private Links links;

    public BlocksResponse() {}

    public BlocksResponse blocks(@jakarta.annotation.Nullable List<@Valid Block> blocks) {
        this.blocks = blocks;
        return this;
    }

    public BlocksResponse addBlocksItem(Block blocksItem) {
        if (this.blocks == null) {
            this.blocks = new ArrayList<>();
        }
        this.blocks.add(blocksItem);
        return this;
    }

    /**
     * Get blocks
     * @return blocks
     */
    @jakarta.annotation.Nullable
    @Valid
    @JsonProperty(JSON_PROPERTY_BLOCKS)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public List<@Valid Block> getBlocks() {
        return blocks;
    }

    @JsonProperty(JSON_PROPERTY_BLOCKS)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setBlocks(@jakarta.annotation.Nullable List<@Valid Block> blocks) {
        this.blocks = blocks;
    }

    public BlocksResponse links(@jakarta.annotation.Nullable Links links) {
        this.links = links;
        return this;
    }

    /**
     * Get links
     * @return links
     */
    @jakarta.annotation.Nullable
    @Valid
    @JsonProperty(JSON_PROPERTY_LINKS)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public Links getLinks() {
        return links;
    }

    @JsonProperty(JSON_PROPERTY_LINKS)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public void setLinks(@jakarta.annotation.Nullable Links links) {
        this.links = links;
    }

    /**
     * Return true if this BlocksResponse object is equal to o.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BlocksResponse blocksResponse = (BlocksResponse) o;
        return Objects.equals(this.blocks, blocksResponse.blocks) && Objects.equals(this.links, blocksResponse.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blocks, links);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class BlocksResponse {\n");
        sb.append("    blocks: ").append(toIndentedString(blocks)).append("\n");
        sb.append("    links: ").append(toIndentedString(links)).append("\n");
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
