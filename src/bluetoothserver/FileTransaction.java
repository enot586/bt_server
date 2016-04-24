package reportserver;

import org.json.simple.JSONObject;

class FileTransaction extends SimpleTransaction {

    private String fileName;

    FileTransaction(JSONObject header_, String fileName) {
        super.header = header_;
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

}
