package reportserver;

import org.json.simple.JSONObject;

import java.util.NoSuchElementException;

public interface CommonUserInterface {
    public void sendUserMessage(String text);
    public JSONObject popUserMessage() throws NoSuchElementException;
}
