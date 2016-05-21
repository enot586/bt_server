package reportserver;

import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class BluetoothTransactionHandlerTest {
    private TestStubUserInterface testUi = new TestStubUserInterface();
    private TestStubBluetoothServer testBluetoothServer = new TestStubBluetoothServer(testUi);
    private DatabaseDriver testDatabaseDriver = new DatabaseDriver();
    private BluetoothTransactionHandler bluetoothTransactionHandler =
            new BluetoothTransactionHandler(testBluetoothServer, testDatabaseDriver, testUi);

    @Before
    public void setUp() throws Exception {
        try {
            testDatabaseDriver.init(ProjectDirectories.test_commonDatabaseRelativePath,
                    ProjectDirectories.test_localDatabaseRelativePath);

            testDatabaseDriver.initDatabaseVersion("AABBCCDDEEFF");
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
    public void bluetoothSynchTransactionHandler_clientVersionLessDatabase() throws Exception {
        testBluetoothServer.setRemoteDeviceBluetoothAddress("<<<TEST_ADDRESS2>>>");

        //SYNCH_REQUEST
        JSONObject header = new JSONObject();
        header.put( "type", new Long(BluetoothPacketType.SYNCH_REQUEST.getId()) );
        header.put( "userId", new Long(3) );
        header.put( "version", new Long(1) );

        SimpleTransaction t = new SimpleTransaction(header);
        testBluetoothServer.pushReceivedTransaction(t);

        bluetoothTransactionHandler.run();

        //SQL_QUERIES {"type":0, "userId":3, "version":testDatabaseDriver.getDatabaseVersion()}
        t = testBluetoothServer.getFirstSendTransaction();
        testBluetoothServer.removeFirstSendTransaction();

        assertNotNull(t);
        assertEquals( ((Long) t.getHeader().get("type")).intValue(), BluetoothPacketType.SQL_QUERIES.getId() );
        assertEquals( ((Long) t.getHeader().get("userId")).intValue(), 3 );
        assertEquals( ((Long) t.getHeader().get("version")).intValue(), testDatabaseDriver.getDatabaseVersion() );

        String testLine;

        try (FileReader fr = new FileReader(((FileTransaction)t).getFileName())) {
            try(BufferedReader br = new BufferedReader(fr)) {
                testLine = br.readLine();
            }
        }

        assertNotNull(testLine);

        String[] list = testLine.split(";");

        assertEquals(list.length, 5);

        assertEquals(list[0], "TEST_QUERY2");
        assertEquals(list[1], "TEST_QUERY2");
        assertEquals(list[2], "TEST_QUERY3");
        assertEquals(list[3], "TEST_QUERY3");
        assertEquals(list[4], "TEST_QUERY3");

        //RESPONSE
        JSONObject responseHeader = new JSONObject();
        responseHeader.put( "type", new Long(BluetoothPacketType.RESPONSE.getId()) );
        responseHeader.put( "userId", new Long(3) );
        responseHeader.put( "status", new Long(TransactionStatus.DONE.getId()) );
        SimpleTransaction response_t = new SimpleTransaction(responseHeader);
        testBluetoothServer.pushReceivedTransaction(response_t);

        bluetoothTransactionHandler.run();

        //BINARY_FILE {"type":3, "userId":3, "filename":"test_file.jpg"}
        t = testBluetoothServer.getFirstSendTransaction();
        testBluetoothServer.removeFirstSendTransaction();

        assertNotNull(t);
        assertEquals( ((Long) t.getHeader().get("type")).intValue(), BluetoothPacketType.BINARY_FILE.getId() );
        assertEquals( ((Long) t.getHeader().get("userId")).intValue(), 3 );

        File file = new File( (String) t.getHeader().get("filename") );
        assertEquals( file.getName(), "test_file.jpg" );

        //RESPONSE
        testBluetoothServer.pushReceivedTransaction(response_t);

        bluetoothTransactionHandler.run();

        //END_TRANSACTION
        t = testBluetoothServer.getFirstSendTransaction();
        testBluetoothServer.removeFirstSendTransaction();

        assertNotNull(t);
        assertEquals( ((Long) t.getHeader().get("type")).intValue(), BluetoothPacketType.END_TRANSACTION.getId() );
        assertEquals( ((Long) t.getHeader().get("version")).intValue(), testDatabaseDriver.getDatabaseVersion() );
    }

    @Test
    public void bluetoothSynchTransactionHandler_clientVersionNotEqDatabase() throws Exception {
        testBluetoothServer.setRemoteDeviceBluetoothAddress("<<<TEST_ADDRESS1>>>");

        //SYNCH_REQUEST
        JSONObject header = new JSONObject();
        header.put( "type", new Long(BluetoothPacketType.SYNCH_REQUEST.getId()) );
        header.put( "userId", new Long(3) );
        header.put( "version", new Long(2) );

        SimpleTransaction t = new SimpleTransaction(header);
        testBluetoothServer.pushReceivedTransaction(t);

        bluetoothTransactionHandler.run();

        //REPLACE_DATABASE {"type":5, "userId":3, "size":1, "version":testDatabaseDriver.getDatabaseVersion()}
        t = testBluetoothServer.getFirstSendTransaction();
        testBluetoothServer.removeFirstSendTransaction();

        assertNotNull(t);
        assertEquals( ((Long) t.getHeader().get("type")).intValue(), BluetoothPacketType.REPLACE_DATABASE.getId() );
        assertEquals( ((Long) t.getHeader().get("userId")).intValue(), 3 );
        assertEquals( ((Long) t.getHeader().get("version")).intValue(), testDatabaseDriver.getDatabaseVersion() );

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

        File file = new File( (String) t.getHeader().get("filename") );
        assertEquals( file.getName(), "test_file.jpg" );

        //RESPONSE
        testBluetoothServer.pushReceivedTransaction(response_t);

        bluetoothTransactionHandler.run();

        //SESSION_CLOSE
        t = testBluetoothServer.getFirstSendTransaction();
        testBluetoothServer.removeFirstSendTransaction();

        assertNotNull(t);
        assertEquals( ((Long) t.getHeader().get("type")).intValue(), BluetoothPacketType.SESSION_CLOSE.getId() );
        assertEquals( ((Long) t.getHeader().get("version")).intValue(), testDatabaseDriver.getDatabaseVersion() );
    }

    @Test
    public void bluetoothSynchTransactionHandler_clientVersionEqDatabase() throws Exception {
        testBluetoothServer.setRemoteDeviceBluetoothAddress("<<<TEST_ADDRESS1>>>");

        //SYNCH_REQUEST
        JSONObject header = new JSONObject();
        header.put( "type", new Long(BluetoothPacketType.SYNCH_REQUEST.getId()) );
        header.put( "userId", new Long(3) );
        header.put( "version", new Long(testDatabaseDriver.getDatabaseVersion()) );

        SimpleTransaction t = new SimpleTransaction(header);
        testBluetoothServer.pushReceivedTransaction(t);

        bluetoothTransactionHandler.run();

        //END_TRANSACTION {"type":6, "version":testDatabaseDriver.getDatabaseVersion()}
        t = testBluetoothServer.getFirstSendTransaction();
        testBluetoothServer.removeFirstSendTransaction();

        assertNotNull(t);
        assertEquals( ((Long) t.getHeader().get("type")).intValue(), BluetoothPacketType.END_TRANSACTION.getId() );
        assertEquals( ((Long) t.getHeader().get("version")).intValue(), testDatabaseDriver.getDatabaseVersion() );
    }

    @Test
    public void bluetoothSqlQueriesTransactionHandler() throws Exception {

    }




}