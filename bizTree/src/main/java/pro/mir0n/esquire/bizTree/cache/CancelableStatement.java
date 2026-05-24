/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/23/2026 mir0n  created: a prepared statement bundled with the connection it was opened on, so the
 *                   caller closes BOTH deterministically (the night-watch cancelable CHECKSUM). Replaces
 *                   the fragile "open the connection in the repository, close it via ps.getConnection()
 *                   in the caller" pattern that relied on driver identity + pool statement cleanup.
 */
package pro.mir0n.esquire.bizTree.cache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * A {@link PreparedStatement} plus the {@link Connection} it was opened on. AutoCloseable: {@link #close()}
 * closes the statement and then returns the connection to the pool -- so a try-with-resources in the
 * caller guarantees both are released on every path (including when {@code executeQuery()} throws after
 * a cancel). The statement is exposed so the night-watch can register its {@code cancel()} and run it.
 */
public record CancelableStatement(Connection connection, PreparedStatement statement) implements AutoCloseable {

    @Override
    public void close() throws SQLException {
        try {
            statement.close();
        } finally {
            connection.close();
        }
    }
}
