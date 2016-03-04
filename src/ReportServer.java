import java.io.IOException;


public class ReportServer {

    private static WebServer webServer;
    private static BluetoothServer bluetoothServer;

    public static CommonServer.ServerState getWebServerState() {
        return webServer.getServerState();
    }

    public static CommonServer.ServerState getBluetoothServerState() {
        return bluetoothServer.getServerState();
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        webServer = new WebServer(8080, "/webapp");
        bluetoothServer = new BluetoothServer();

        try {
            webServer.init();
            if (webServer.getServerState() == CommonServer.ServerState.SERVER_READY_NOT_ACTIVE)
                webServer.start();

            bluetoothServer.init();
            if (bluetoothServer.getServerState() == CommonServer.ServerState.SERVER_READY_NOT_ACTIVE)
                bluetoothServer.start();
        }
        catch(Exception e) {

        }

        while(true) {

        }
    }

}
