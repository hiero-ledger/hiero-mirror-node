// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.getAbiFunctionAsJsonString;

import com.esaulpaugh.headlong.abi.Function;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.hiero.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource;
import org.hiero.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;

public abstract class EncoderDecoderFacade {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    protected ObjectMapper mapper;

    protected CompiledSolidityArtifact readCompiledArtifact(InputStream in) throws IOException {
        return mapper.readValue(in, CompiledSolidityArtifact.class);
    }

    protected InputStream getResourceAsStream(String resourcePath) throws IOException {
        return resourceLoader.getResource(resourcePath).getInputStream();
    }

    protected byte[] encodeDataToByteArray(ContractResource resource, SelectorInterface method, Object... args) {
        String json;
        try (var in = getResourceAsStream(resource.getPath())) {
            json = getAbiFunctionAsJsonString(readCompiledArtifact(in), method.getSelector());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Function function = Function.fromJson(json);
        ByteBuffer byteBuffer = function.encodeCallWithArgs(args);
        return byteBuffer.array();
    }
}
