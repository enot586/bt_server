package reportserver;

import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


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
        public void reopenNewConnection() {
        }

        @Override
        public String getRemoteDeviceBluetoothAddress() throws IOException {
            return new String("112233445566");
        }
    }

    class TestDatabaseDriver extends DatabaseDriver {
        TestDatabaseDriver() {
        }

        @Override
        public synchronized int checkClientVersion(String mac_address) throws SQLException {
            return 1;
        }

        @Override
        public synchronized int getDatabaseVersion() {
            return 1;
        }

        @Override
        public synchronized void setClientVersion(String mac_address, int id_version) throws SQLException {
        }

        @Override
        public synchronized ArrayList<String> getClientPicturesHistory(int version) throws SQLException {
            return new ArrayList<String>();
        }
    }

    private TestUserInterface testUi = new TestUserInterface();
    private BluetoothServer testBluetoothServer = new TestBluetoothServer(testUi);
    private TestDatabaseDriver testDatabaseDriver = new TestDatabaseDriver();
    private BluetoothTransactionHandler bluetoothTransactionHandler =
            new BluetoothTransactionHandler(testBluetoothServer, testDatabaseDriver, testUi);

    @Before
    public void setUp() throws Exception {
        try {
            testDatabaseDriver.init(ProjectDirectories.test_commonDatabaseRelativePath,
                    ProjectDirectories.test_localDatabaseRelativePath);
        } catch (SQLException e) {
            assertTrue(false);
        }
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
    public void bluetoothSynchTransactionHandler_clientVersionNotEqDatabase() throws Exception {
        //SYNCH_REQUEST
        JSONObject header = new JSONObject();
        header.put("type", new Long(BluetoothPacketType.SYNCH_REQUEST.getId()));
        header.put("userId", new Long(3));
        header.put("version", new Long(3));
        SimpleTransaction t = new SimpleTransaction(header);
        testBluetoothServer.pushReceivedTransaction(t);

        bluetoothTransactionHandler.run();

        //REPLACE_DATABASE {"type":5, "userId":3, "size":1, "version":1}
        t = testBluetoothServer.getFirstSendTransaction();
        testBluetoothServer.removeFirstSendTransaction();

        assertNotNull(t);
        assertEquals( ((Long) t.getHeader().get("type")).intValue(), BluetoothPacketType.REPLACE_DATABASE.getId() );
        assertEquals( ((Long) t.getHeader().get("userId")).intValue(), 3 );
        assertEquals( ((Long) t.getHeader().get("version")).intValue(), 1 );

        //RESPONSE
        JSONObject responseHeader = new JSONObject();
        responseHeader.put( "type", new Long(BluetoothPacketType.RESPONSE.getId()) );
        responseHeader.put( "userId", new Long(3) );
        responseHeader.put( "status", new Long(TransactionStatus.DONE.getId()) );
        SimpleTransaction response_t = new SimpleTransaction(responseHeader);
        testBluetoothServer.pushReceivedTransaction(response_t);

        bluetoothTransactionHandler.run();

        //BINARY_FILE {"type":3, "userId":3, "size":1, "filename":"test_file.jpg"}
        t = testBluetoothServer.getFirstSendTransaction();
        testBluetoothServer.removeFirstSendTransaction();

        assertNotNull(t);
        assertEquals( ((Long) t.getHeader().get("type")).intValue(), BluetoothPacketType.BINARY_FILE.getId() );
        assertEquals( ((Long) t.getHeader().get("userId")).intValue(), 3 );

        File file = new File((String) t.getHeader().get("filename"));
        assertEquals( file.getName(), new String("test_file.jpg") );

        //RESPONSE
        testBluetoothServer.pushReceivedTransaction(response_t);

        bluetoothTransactionHandler.run();

        //RESPONSE
        testBluetoothServer.pushReceivedTransaction(response_t);

        bluetoothTransactionHandler.run();

        //SESSION_CLOSE
        t = testBluetoothServer.getFirstSendTransaction();
        testBluetoothServer.removeFirstSendTransaction();

        assertNotNull(t);
        assertEquals( ((Long) t.getHeader().get("type")).intValue(), BluetoothPacketType.SESSION_CLOSE.getId() );
        assertEquals( ((Long) t.getHeader().get("version")).intValue(), 1 );
    }

    @Test
    public void bluetoothSynchTransactionHandler_clientVersionEqDatabase() throws Exception {
        //SYNCH_REQUEST
        JSONObject header = new JSONObject();
        header.put("type", new Long(BluetoothPacketType.SYNCH_REQUEST.getId()));
        header.put("userId", new Long(3));
        header.put("version", new Long(1));
        SimpleTransaction t = new SimpleTransaction(header);
        testBluetoothServer.pushReceivedTransaction(t);

        bluetoothTransactionHandler.run();

        //END_TRANSACTION {"type":6, "version":1}
        t = testBluetoothServer.getFirstSendTransaction();
        testBluetoothServer.removeFirstSendTransaction();

        assertNotNull(t);
        assertEquals( ((Long) t.getHeader().get("type")).intValue(), BluetoothPacketType.END_TRANSACTION.getId() );
        assertEquals( ((Long) t.getHeader().get("version")).intValue(), 1 );
    }

    @Test
    public void bluetoothSqlQueriesTransactionHandler() throws Exception {

    }




}