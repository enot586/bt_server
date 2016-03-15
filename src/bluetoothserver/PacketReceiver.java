package reportserver;

import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.Arrays;

public class PacketReceiver {

    private enum ReceiverFsm {
        RECEIVER_SEARCH_HEADER,
        RECEIVER_HEADER,
        RECEIVER_BODY,
    }
    private ReceiverFsm receivePacketFsm = ReceiverFsm.RECEIVER_SEARCH_HEADER;

    private JSONObject header;
    private StringBuffer jsonString = new StringBuffer();

    private int bodyBytesCounter = 0;
    private boolean isJSONHeaderReceived = false;
    private int byteIndex = 0;

    private JSONObject checkJSON(String str) throws ParseException {
        JSONParser parser = new JSONParser();
        JSONObject jsonHeader = (JSONObject) parser.parse(str);
        return jsonHeader;
    }

    public boolean isHeaderReceived() {
        return isJSONHeaderReceived;
    }

    public JSONObject getHeader() {
        return header;
    }

    public int getHeaderSizeInBytes() {
        return jsonString.length();
    }

    public boolean receiveHeader(byte[] packetBuffer) {

        while (byteIndex < packetBuffer.length) {

            switch (receivePacketFsm) {

                case RECEIVER_SEARCH_HEADER: {
                    if (packetBuffer[byteIndex] == '{') {
                        receivePacketFsm = ReceiverFsm.RECEIVER_HEADER;
                        break;
                    }
                    ++byteIndex;
                    break;
                }

                case RECEIVER_HEADER: {
                    try {
                        header = checkJSON(jsonString.append((char) packetBuffer[byteIndex]).toString());

                        boolean isCorrectJSONFormat =   (null != header.get("type")) &&
                                (null != header.get("size")) &&
                                (null != header.get("userId"));

                        if (isCorrectJSONFormat) {
                            isJSONHeaderReceived = true;
                            //if ((int)header.get("size") > 0) {
                                receivePacketFsm = ReceiverFsm.RECEIVER_BODY;
                                ++byteIndex;
                                return true;
                            //}
                        }
                        else
                        {
                            receivePacketFsm = ReceiverFsm.RECEIVER_SEARCH_HEADER;
                        }
                    } catch (ParseException e) {
                        if (jsonString.length() > 200) {
                            jsonString.delete(0, jsonString.length());
                            receivePacketFsm = ReceiverFsm.RECEIVER_SEARCH_HEADER;
                        }
                    }

                    ++byteIndex;
                    break;
                }
            }
        }
        byteIndex = 0;
        return false;
    }

    public byte[] receiveBody(byte[] packetBuffer) {
        switch (receivePacketFsm) {
             case RECEIVER_BODY: {
                try {
                    byte[] result = Arrays.copyOfRange(packetBuffer, byteIndex, packetBuffer.length);
                    bodyBytesCounter+= (packetBuffer.length - byteIndex+1);
                    return result;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
        }
        return null;
    }

}
