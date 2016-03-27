package reportserver;

import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.Arrays;

class JSONReceiver {

    private enum ReceiverFsm {
        RECEIVER_SEARCH_HEADER,
        RECEIVER_HEADER,
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

    public int receiveHeader(byte[] packetBuffer, int size) {
        byteIndex = 0;
        while (byteIndex < size) {

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

                        if (header.containsKey("type")) {
                            isJSONHeaderReceived = true;
                             return byteIndex;
                        }
                        else
                        {
                            receivePacketFsm = ReceiverFsm.RECEIVER_SEARCH_HEADER;
                        }
                    } catch (ParseException e) {
                        if (jsonString.length() > 500) {
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
        return packetBuffer.length;
    }


}
