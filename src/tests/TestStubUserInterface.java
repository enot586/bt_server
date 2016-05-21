package reportserver;

import org.json.simple.JSONObject;

import java.util.NoSuchElementException;

public class TestStubUserInterface implements CommonUserInterface {
    @Override
    public void sendUserMessage(String text) {

    }

    @Override
    public JSONObject popUserMessage() throws NoSuchElementException {
        return null;
    }
}
