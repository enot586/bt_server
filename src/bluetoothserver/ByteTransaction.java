package reportserver;

import org.json.simple.JSONObject;

class ByteTransaction extends SimpleTransaction {
    private byte[] body;

    ByteTransaction(JSONObject header_, byte[] body_) {
        super.header = header_;
        body = body_;
    }

    public byte[] getBody() {
        return body;
    }
}
