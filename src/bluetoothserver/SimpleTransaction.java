package reportserver;

import org.json.simple.JSONObject;

class SimpleTransaction {

    protected JSONObject header;

    SimpleTransaction() {
        
    }

    SimpleTransaction(JSONObject header_) {
        header = header_;
    }
    JSONObject getHeader() {
        return header;
    }

}
