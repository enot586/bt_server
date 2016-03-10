package reportserver;

import javax.bluetooth.RemoteDevice;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;


import java.net.URL;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.NoSuchElementException;


public class ReportServer {

    private static WebServer webServer;
    private static BluetoothServer bluetoothServer;
    private static ReportDatabaseDriver reportDatabaseDriver;
    private static SqlCommandList sqlScript;

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
            webServer = new WebServer(8080, "webapp");
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

            receiveFileHandler();

            //TODO: мониторим сервера
        }
    }

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

    private static File getReceviedFileFromBluetooth(BluetoothServer bt) throws NoSuchElementException, FileNotFoundException {
        String newReceivedFileName = "exp-db.sql";//bt.popReceiveFileName();
        URL synchDataBaseFile = ReportServer.class.getClassLoader().getResource("base-synchronization");
        if (synchDataBaseFile == null) throw new FileNotFoundException();
        return (new File(synchDataBaseFile.getFile()+"/"+newReceivedFileName) );
    }

    private static void receiveFileHandler() {
        try {
            File scriptFile = getReceviedFileFromBluetooth(bluetoothServer);
            sqlScript = new SqlCommandList(scriptFile);

//            try {
//                String remoteDiviceAddress = bluetoothServer.getRemoteDeviceBluetoothAddress();
                reportDatabaseDriver.BackupCurrentDatabase("0000"/*remoteDiviceAddress*/);
//            } catch (IOException e) {
//                e.printStackTrace();
//                return;
//            }

            reportDatabaseDriver.RunScript(sqlScript);
        } catch (NoSuchElementException e1) {

        } catch (SQLSyntaxErrorException | FileNotFoundException e2) {
            e2.printStackTrace();
        }
    }

}
