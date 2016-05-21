package reportserver;


import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

public class TestStubDatabaseDriver extends DatabaseDriver {

    @Override
    public synchronized void backupCurrentDatabase(String uniqPart) {
    }

    @Override
    public synchronized void replaceCommonBase(File newBase) throws IOException {
    }

    @Override
    public synchronized void removeLocalHistory() throws SQLException {
    }
}
