import java.io.IOException;


public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {

        WebServer webServer = new WebServer(8080, "/webapp");
        BluetoothServer bluetoothServer = new BluetoothServer();

        try {
            webServer.init();
            webServer.start();

            bluetoothServer.init();
            bluetoothServer.start();
        }
        catch(Exception e) {

        }

        while(true) {

        }
    }

}
