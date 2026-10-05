// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.tableusage;

import java.io.Serializable;
import org.springframework.data.jdbc.repository.support.JdbcRepositoryFactoryBean;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.util.Assert;

public final class TrackingRepositoryFactoryBean<R extends Repository<T, I>, T, I extends Serializable>
        extends JdbcRepositoryFactoryBean<R, T, I> {

    private RelationalMappingContext relationalMappingContext;

    /**
     * Creates a new {@link JdbcRepositoryFactoryBean} for the given repository interface.
     *
     * @param repositoryInterface must not be {@literal null}.
     */
    public TrackingRepositoryFactoryBean(final Class<? extends R> repositoryInterface) {
        super(repositoryInterface);
    }

    @Override
    public void setMappingContext(final RelationalMappingContext mappingContext) {
        super.setMappingContext(mappingContext);
        this.relationalMappingContext = mappingContext;
    }

    @Override
    protected RepositoryFactorySupport doCreateRepositoryFactory() {
        RepositoryFactorySupport factory = super.doCreateRepositoryFactory();
        Assert.state(
                relationalMappingContext != null,
                "RelationalMappingContext must be set before the repository factory is created");
        factory.addRepositoryProxyPostProcessor(new TrackingRepositoryProxyPostProcessor(relationalMappingContext));
        return factory;
    }
}
