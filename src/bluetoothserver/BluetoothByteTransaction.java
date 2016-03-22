package reportserver;

import org.json.simple.JSONObject;

class BluetoothByteTransaction extends BluetoothSimpleTransaction {
    private byte[] body;

    BluetoothByteTransaction(JSONObject header_, byte[] body_) {
        super.header = header_;
        body = body_;
    }

    public byte[] getBody() {
        return body;
    }
}
