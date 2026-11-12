package ac.cult.cultac.manager.datastore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ac.grim.grimac.api.storage.backend.BackendConfig;
import ac.grim.grimac.api.storage.backend.BackendContext;
import ac.grim.grimac.internal.storage.backend.sqlite.SqliteBackendConfig;
import ac.grim.grimac.internal.storage.backend.sqlite.v2.SqliteBackendV2;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteSynchronousModeTest {
    private record Context(BackendConfig config, Logger logger, Path dataDirectory) implements BackendContext {}

    @TempDir
    Path folder;

    @Test
    void v2BackendLeavesSqliteDefaultUntilTheConfiguredModeIsApplied() throws Exception {
        SqliteBackendV2 backend = open(config("NORMAL"));
        try {
            // SQLite's default is FULL (2): every WAL commit is synced to disk.
            assertEquals(2, synchronous(backend));
            SharedDataStoreLifecycle.applySqliteSynchronous(backend, config("NORMAL"), Logger.getAnonymousLogger());
            assertEquals(1, synchronous(backend));
        } finally {
            backend.close();
        }
    }

    @Test
    void configuredModeIsReadCaseInsensitivelyAndUnknownModesKeepTheDefault() throws Exception {
        SqliteBackendV2 backend = open(config(" full "));
        try {
            SharedDataStoreLifecycle.applySqliteSynchronous(backend, config("off"), Logger.getAnonymousLogger());
            assertEquals(0, synchronous(backend));
            SharedDataStoreLifecycle.applySqliteSynchronous(backend, config("sometimes"), Logger.getAnonymousLogger());
            assertEquals(0, synchronous(backend));
            SharedDataStoreLifecycle.applySqliteSynchronous(backend, config(" full "), Logger.getAnonymousLogger());
            assertEquals(2, synchronous(backend));
        } finally {
            backend.close();
        }
    }

    private SqliteBackendConfig config(String synchronousMode) {
        SqliteBackendConfig defaults = SqliteBackendConfig.defaults("history.v1.db");
        return new SqliteBackendConfig(
                defaults.path(),
                defaults.journalMode(),
                synchronousMode,
                defaults.busyTimeoutMs(),
                defaults.cachePages(),
                defaults.batchFlushCap(),
                defaults.tableNames());
    }

    private SqliteBackendV2 open(SqliteBackendConfig config) throws Exception {
        SqliteBackendV2 backend = new SqliteBackendV2(config);
        backend.init(new Context(config, Logger.getAnonymousLogger(), folder));
        return backend;
    }

    private static int synchronous(SqliteBackendV2 backend) throws Exception {
        DataSource source = backend.unwrap(DataSource.class).orElseThrow();
        try (Connection connection = source.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA synchronous")) {
            result.next();
            return result.getInt(1);
        }
    }
}
