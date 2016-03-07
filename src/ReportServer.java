package reportserver;

import java.io.IOException;


import java.net.URL;
import java.sql.SQLException;


public class ReportServer {

    private static WebServer webServer;
    private static BluetoothServer bluetoothServer;
    private static ReportDatabaseDriver reportDatabaseDriver;

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
            e.printStackTrace();
        }
    }

    public static void bluetoothServerStop() throws Exception {
        try {
            bluetoothServer.stop();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static ReportDatabaseDriver getDatabaseDriver() throws SQLException {
        return reportDatabaseDriver;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        reportDatabaseDriver = new ReportDatabaseDriver();

        URL databaseDir = ReportServer.class.getResource("/app-data.db3");

        try {
            reportDatabaseDriver.init(databaseDir.toString());
        } catch (SQLException e) {
            e.printStackTrace();
        }

        webServer = new WebServer(8080, "/");
        bluetoothServer = new BluetoothServer();

        try {
            bluetoothServer.init();

            webServer.init();
            webServer.start();
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        while (true) {
            //@TODO: мониторим сервера
        }
    }

}
