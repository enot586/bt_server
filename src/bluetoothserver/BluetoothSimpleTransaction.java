package reportserver;

import org.json.simple.JSONObject;

class BluetoothSimpleTransaction {

    protected JSONObject header;

    BluetoothSimpleTransaction() {
        
    }

    BluetoothSimpleTransaction(JSONObject header_) {
        header = header_;
    }
    JSONObject getHeader() {
        return header;
    }

}
