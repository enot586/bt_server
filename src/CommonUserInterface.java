package reportserver;

import org.json.simple.JSONObject;

import java.util.NoSuchElementException;

public interface CommonUserInterface {
    void sendUserMessage(String text);
    JSONObject popUserMessage() throws NoSuchElementException;
}
