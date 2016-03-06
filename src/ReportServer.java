import java.io.IOException;


public class ReportServer {

    private static WebServer webServer;
    private static BluetoothServer bluetoothServer;

    public static CommonServer.ServerState webServerGetState() {
        return webServer.getServerState();
    }

    public static CommonServer.ServerState bluetoothServerGetState() {
        return bluetoothServer.getServerState();
    }

    public static void bluetoothServerStart() throws Exception {
        try {
            bluetoothServer.start();
        } catch(Exception e) {

        }
    }

    public static void bluetoothServerStop() throws Exception {
        try {
            bluetoothServer.stop();
        } catch(Exception e) {

        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        webServer = new WebServer(8080, "/webapp");
        bluetoothServer = new BluetoothServer();

        try {
            bluetoothServer.init();

            webServer.init();
            if (webServer.getServerState() == CommonServer.ServerState.SERVER_READY_NOT_ACTIVE)
                webServer.start();
        }
        catch(Exception e) {

        }

        while(true) {

        }
    }

}
