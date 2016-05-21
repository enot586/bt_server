package reportserver;

import java.io.IOException;

public class TestStubBluetoothServer extends BluetoothServer {

    private String remoteAddress = new String("<<<TEST_ADDRESS1>>>");

    public TestStubBluetoothServer(CommonUserInterface ui_) {
        super(ui_);
    }

    @Override
    public void reopenNewConnection() {
    }

    @Override
    public String getRemoteDeviceBluetoothAddress() throws IOException {
        return remoteAddress;
    }

    public void setRemoteDeviceBluetoothAddress(String mac) {
        remoteAddress = mac;
    }
}
