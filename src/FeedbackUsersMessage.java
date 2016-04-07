package reportserver;

import org.json.simple.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.NoSuchElementException;


public class FeedbackUsersMessage implements CommonUserInterface {

    private DatabaseDriver dbd;
    private final LinkedList<JSONObject> userMessages = new LinkedList<JSONObject>();

    FeedbackUsersMessage(DatabaseDriver dbd_) {
        dbd = dbd_;
    }

    private void addUserMessage(Date date, String text) {
        JSONObject userMessage = new JSONObject();
        String pattern = "yyyy-MM-dd HH:mm:ss.SSS";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        String strDate = simpleDateFormat.format(date);
        userMessage.put("date", strDate);
        userMessage.put("text", text);
        synchronized (userMessages) {
            userMessages.add(userMessage);
        }
    }

    @Override
    public void sendUserMessage(String text) {
        try {
            Date date = new Date();
            //Выбираем нужный web-сервер и отправляем ему текст
            addUserMessage(date, text);
            //отправляем в базу
//            dbd.addUserMessageToDatabase(date, text);
        } catch (Exception e) {

        }
    }

    public synchronized JSONObject popUserMessage() throws NoSuchElementException {
        JSONObject text = userMessages.peek();
        if (text == null)  throw new NoSuchElementException();
        userMessages.remove();
        return text;
    }
}
