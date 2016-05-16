package reportserver;

import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.NoSuchElementException;


public class BluetoothTransactionHandlerTest {

    class TestUserInterface implements CommonUserInterface {

        @Override
        public void sendUserMessage(String text) {

        }

        @Override
        public JSONObject popUserMessage() throws NoSuchElementException {
            return null;
        }
    }

    class TestBluetoothServer extends BluetoothServer {
        TestBluetoothServer(CommonUserInterface ui_) {
            super(ui_);
        }

        @Override
        public synchronized boolean sendData(SimpleTransaction t) {
            return true;
        }

        @Override
        public synchronized boolean sendData(GroupTransaction t) {
            return true;
        }
    }

    class TestDatabaseDriver extends DatabaseDriver {
        TestDatabaseDriver() {

        }
    }

    TestUserInterface testUi = new TestUserInterface();
    BluetoothServer testBluetoothServer = new TestBluetoothServer(testUi);
    TestDatabaseDriver testDatabaseDriver = new TestDatabaseDriver();

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void run() throws Exception {

    }

    @Test
    public void bluetoothReplaceDatabaseTransactionHandler() throws Exception {

    }

    @Test
    public void bluetoothBinaryFileTransactionHandler() throws Exception {

    }

    @Test
    public void bluetoothSynchTransactionHandler() throws Exception {
        BluetoothTransactionHandler bluetoothTransactionHandler = new BluetoothTransactionHandler(testBluetoothServer,
                                                                                                  testDatabaseDriver,
                                                                                                  testUi);
    }

    @Test
    public void bluetoothSqlQueriesTransactionHandler() throws Exception {

    }

    @Test
    public void addSqlHistoryToGroupTransaction() throws Exception {

    }

    @Test
    public void addPicturesToGroupTransaction() throws Exception {

    }

    @Test
    public void addReplaceDatabaseToGroupTransaction() throws Exception {

    }

    @Test
    public void addEndTransactionToGroupTransaction() throws Exception {

    }

    @Test
    public void addSessionCloseToGroupTransaction() throws Exception {

    }

    @Test
    public void addPicturesFromTableToGroupTransaction() throws Exception {

    }

}