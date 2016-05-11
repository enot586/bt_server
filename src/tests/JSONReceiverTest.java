package reportserver;

import org.json.simple.parser.JSONParser;
import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;


public class JSONReceiverTest {

    private final JSONReceiver jr = new JSONReceiver();

    private final String testRecevierPattern = new String("...SomeText{\"type\":5, \"userId\":2, \"size\":666, \"version\":3}SomeText...");

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void receiveHeader() throws Exception {
        byte[] receiveString = testRecevierPattern.getBytes();
        for (int i = 0; i < testRecevierPattern.length()-1; ++i) {
            jr.receiveHeader(Arrays.copyOfRange(receiveString, i, i+1), 1);

            if (jr.isHeaderReceived()) {
                JSONObject header = jr.getHeader();

                assertEquals(5, (long)header.get("type"));
                assertEquals(2, (long)header.get("userId"));
                assertEquals(666, (long)header.get("size"));
                assertEquals(3, (long)header.get("version"));

                return;
            }
        }

        assertTrue(false);
    }

}