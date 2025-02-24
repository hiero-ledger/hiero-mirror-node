// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.generator;

import com.hedera.mirror.monitor.publish.PublishRequest;
import com.hedera.mirror.monitor.publish.PublishScenario;
import java.util.List;
import reactor.core.publisher.Flux;

public interface TransactionGenerator {

    /**
     * Gets the next count publish requests. If count > 0, up to count publish requests will be generated; if count <=
     * 0, the generator will determine the actual count.
     *
     * @param count
     * @return
     */
    List<PublishRequest> next(int count);

    default List<PublishRequest> next() {
        return next(1);
    }

    Flux<PublishScenario> scenarios();
}
