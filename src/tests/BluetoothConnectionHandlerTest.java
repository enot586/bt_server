package reportserver;

import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class BluetoothConnectionHandlerTest {

    class TestUserInterface implements CommonUserInterface {

        @Override
        public void sendUserMessage(String text) {

        }

        @Override
        public JSONObject popUserMessage() throws NoSuchElementException {
            return null;
        }
    }

    private final BluetoothConnectionHandler bch = new BluetoothConnectionHandler(new BluetoothServer(new TestUserInterface()),
                                                                                  "btspp://Test",
                                                                                  new TestUserInterface());

    private final String testRecevierPattern = new String("...SomeText{\"type\":1, \"userId\":2, \"version\":3}SomeText...");

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void sendTransaction() throws Exception {
        JSONObject header = new JSONObject();
        header.put("type", new Long(1));
        header.put("userId", new Long(2));
        header.put("version", new Long(3));

        SimpleTransaction transaction = new SimpleTransaction(header);
        try ( ByteArrayOutputStream bOutput = new ByteArrayOutputStream(100)) {
            try ( BufferedOutputStream bos = new BufferedOutputStream(bOutput) ) {
                bch.sendTransaction(bos, transaction);

                assertEquals("{\"type\":1,\"userId\":2,\"version\":3}", bOutput.toString());
            }
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void dataReceiving() throws Exception {
        try ( InputStream is = new ByteArrayInputStream( testRecevierPattern.getBytes( "UTF-8" ) ) ) {
            try ( BufferedInputStream ibs = new BufferedInputStream(is) ) {
                SimpleTransaction transaction = bch.dataReceiving(ibs);

                assertEquals(1, (long)transaction.getHeader().get("type"));
                assertEquals(2, (long)transaction.getHeader().get("userId"));
                assertEquals(3, (long)transaction.getHeader().get("version"));
            }
        } catch (Exception e) {
            assertTrue(false);
        }
    }

}