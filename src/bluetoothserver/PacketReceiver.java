package reportserver;

import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class PacketReceiver {

    private enum ReceiverFsm {
        RECEIVER_SEARCH_HEADER,
        RECEIVER_HEADER,
        RECEIVER_BODY,
    }
    private ReceiverFsm receivePacketFsm = ReceiverFsm.RECEIVER_SEARCH_HEADER;

    private JSONObject header;
    private StringBuffer jsonString = new StringBuffer();

    private boolean isHeaderReceived = false;
    private int byteIndex = 0;
    private int numberReceivingBodyBytes = 0;

    private JSONObject checkJSON(String str) throws ParseException {
        JSONParser parser = new JSONParser();
        JSONObject jsonHeader = (JSONObject) parser.parse(str);
        return jsonHeader;
    }

    public boolean isJSONHeaderReceived() {
        return isHeaderReceived;
    }

    public JSONObject getHeader() {
        return header;
    }

    public int getHeaderSizeInBytes() {
        return jsonString.length();
    }

    public int receiveHandlerFsm(byte[] packetBuffer, int bufferSize, byte[] destBuffer) {

        while (byteIndex < bufferSize) {

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
                            isHeaderReceived = true;
                            numberReceivingBodyBytes = 0;

                            //if ((int)header.get("size") > 0) {
                                receivePacketFsm = ReceiverFsm.RECEIVER_BODY;
                            //}
                        }
                        else
                        {
                            receivePacketFsm = ReceiverFsm.RECEIVER_SEARCH_HEADER;
                        }
                    } catch (ParseException e) {

                    }

                    ++byteIndex;
                    break;
                }

                case RECEIVER_BODY: {
                    try {
                        numberReceivingBodyBytes = (byteIndex - jsonString.length()+1);
                        destBuffer[numberReceivingBodyBytes-1] = packetBuffer[byteIndex];
                        ++byteIndex;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        return numberReceivingBodyBytes;
    }

}
