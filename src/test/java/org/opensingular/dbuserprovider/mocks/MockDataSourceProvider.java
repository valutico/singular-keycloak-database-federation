package org.opensingular.dbuserprovider.mocks;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Mock implementation of DataSource for testing database operations
 * without requiring an actual database connection.
 */
public class MockDataSourceProvider implements DataSource {
    
    private Connection mockConnection;
    private PrintWriter logWriter;
    private int loginTimeout = 0;
    
    public MockDataSourceProvider(Connection mockConnection) {
        this.mockConnection = mockConnection;
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return mockConnection;
    }
    
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return mockConnection;
    }
    
    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return logWriter;
    }
    
    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        this.logWriter = out;
    }
    
    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        this.loginTimeout = seconds;
    }
    
    @Override
    public int getLoginTimeout() throws SQLException {
        return loginTimeout;
    }
    
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Not implemented");
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }
}