package reportserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;


import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.NoSuchElementException;


public class ReportServer {

    private static WebServer webServer;
    private static BluetoothServer bluetoothServer;
    private static ReportDatabaseDriver reportDatabaseDriver;
    private FileHandler fileHandler;

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

    private static boolean CheckScriptSyntax(File script) {
        return true;
    }
    private static boolean CheckScriptForDataDoubling(File script) {
        return true;
    }

    private static void RunScript(File script) {

    }

    private static void BackupCurrentDataBase() {

    }

    private static void CheckReceviedFileFromBluetooth(BluetoothServer bt) {
        try {
            String newReceivedFileName = bt.popReceiveFileName();

            URL synchDataBaseFile = ReportServer.class.getClassLoader().getResource("base-synchronization");
            File scriptFile = new File(synchDataBaseFile.getFile());

            //FileHandler fileNameHandler = new FileHandler( synchDataBaseFile.getFile() );
            //fileNameHandler.getMD5ForFile(newReceivedFileName);

            if ( CheckScriptSyntax(scriptFile) && CheckScriptForDataDoubling(scriptFile) ) {
                BackupCurrentDataBase();
                RunScript(scriptFile);
            }

        } catch(NoSuchElementException e) {

        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        try {
            reportDatabaseDriver = new ReportDatabaseDriver();
            URL databaseDir = ReportServer.class.getClassLoader().getResource("base-synchronization/app-data.db3");
            reportDatabaseDriver.init(databaseDir.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        } catch(Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            webServer = new WebServer(8080, "/");
            bluetoothServer = new BluetoothServer();

            bluetoothServer.init();

            webServer.init();
            webServer.start();
        }
        catch(Exception e) {
            e.printStackTrace();
            return;
        }

        while (true) {

            CheckReceviedFileFromBluetooth(bluetoothServer);

            //TODO: мониторим сервера
        }
    }

}
