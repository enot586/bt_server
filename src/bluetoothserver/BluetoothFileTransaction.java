package reportserver;

import org.json.simple.JSONObject;

class BluetoothFileTransaction extends BluetoothSimpleTransaction {

    private String fileName;

    BluetoothFileTransaction(JSONObject header_, String fileName) {
        super.header = header_;
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

}
