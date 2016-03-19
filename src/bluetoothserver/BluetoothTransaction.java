package reportserver;

import org.json.simple.JSONObject;

public class BluetoothTransaction {
    public enum Type {
        PARAMS,
        BYTE_ARRAY,
        FILE,
    };

    private Type transactionType;
    private JSONObject header;
    private String bodyFileName;
    private byte[] byteArray;

    BluetoothTransaction(JSONObject header_) {
        header = header_;
        transactionType = Type.PARAMS;
    }

    BluetoothTransaction(JSONObject header_, String fileName) {
        header = header_;
        bodyFileName = fileName;
        transactionType = Type.FILE;
    }

    BluetoothTransaction(JSONObject header_, byte[] byteArray_) {
        header = header_;
        byteArray = byteArray_;
        transactionType = Type.BYTE_ARRAY;
    }

    Type getType() {
        return transactionType;
    }

    JSONObject getHeader() {
        return header;
    }

    String getFileName() {
        if (transactionType != Type.FILE)
            throw new NullPointerException();

        return bodyFileName;
    }

    byte[] getByteArray() {
        if (transactionType != Type.BYTE_ARRAY)
            throw new NullPointerException();

        return byteArray;
    }

}
