// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * A {@link DelegatingDataSource} that intercepts every statement-creating call on the connections it hands out. Each
 * connection is wrapped in a dynamic proxy so {@link #onStatement(Object[])} runs before {@code prepareStatement},
 * {@code prepareCall} and {@code createStatement}; subclasses use the hook to enforce a deadline, rewrite the SQL in
 * {@code args[0]}, etc. Extracted to share the proxy boilerplate between the importer and web3 datasource wrappers.
 */
public abstract class StatementInterceptingDataSource extends DelegatingDataSource {

    private static final Set<String> STATEMENT_METHODS = Set.of("prepareStatement", "prepareCall", "createStatement");

    protected StatementInterceptingDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    /**
     * Invoked before each statement-creating call. {@code args[0]} holds the SQL when the method carries one (i.e. not
     * for {@code createStatement}) and may be rewritten in place.
     *
     * @param args the intercepted method's arguments, possibly {@code null}
     */
    protected abstract void onStatement(Object[] args);

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    private Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    if (STATEMENT_METHODS.contains(method.getName())) {
                        onStatement(args);
                    }

                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                });
    }
}
