package reportserver;

import javax.servlet.AsyncContext;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.simple.JSONObject;

public class ReportServer {

    private static WebServer webServer;
    private static BluetoothServer bluetoothServer;
    private static ReportDatabaseDriver reportDatabaseDriver;
    private static SqlCommandList sqlScript;
    private static Map< WebActionType, AsyncContext > webActions = new HashMap< WebActionType, AsyncContext >();
    private static final Logger log = Logger.getLogger(ReportServer.class);

    public static void main(String[] args) throws IOException, InterruptedException {

//        String ddd = "{\"type\":3,\"size\":10,\"userId\":2}BODY_BODY BODY;###";
//
//        PacketReceiver receiver = new PacketReceiver();
//
//        if (!receiver.isHeaderReceived()) {
//            receiver.receiveHeader(ddd.getBytes());
//        }
//
//        if (receiver.isHeaderReceived()) {
//            JSONObject header = receiver.getHeader();
//            long body_size = (long)header.get("size");
//
//            if (body_size > 0) {
//                byte[] body_mody = receiver.receiveBody(ddd.getBytes());
//                System.out.println(body_mody[3]);
//            }
//        }

        System.out.println("Application status:\t\t[INIT]");
        System.out.println("--------------------------------");

        PropertyConfigurator.configure("log4j.properties");
        log.info("Application started");

        System.out.print("Database driver\t\t");
        try {
            reportDatabaseDriver = new ReportDatabaseDriver();
            reportDatabaseDriver.init("base-synchronization/app-data.db3");
            System.out.print("[OK]\n");
        } catch (SQLException e) {
            System.out.print("[FAIL]\n");
            log.error(e);
            return;
        } catch(Exception e) {
            System.out.print("[FAIL]\n");
            log.error(e);
            return;
        }

        try {
            System.out.print("Web server init\t\t");
            webServer = new WebServer(8080, "webapp");
            webServer.init();
            System.out.print("[OK]\n");

            System.out.print("Bluetooth driver init\t\t");
            bluetoothServer = new BluetoothServer();
            bluetoothServer.init();
            System.out.print("[OK]\n");

            System.out.print("Web server start\t\t");
            webServer.start();
            System.out.print("[OK]\n");
        } catch(Exception e) {
            System.out.print("[FAIL]\n");
            log.error(e);
            return;
        }

        System.out.println("--------------------------------");
        System.out.println("Application status:\t\t[RUNNING]");

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
            log.error(e);
        }
    }

    public static void bluetoothServerStop() throws Exception {
        try {
            bluetoothServer.stop();
        } catch(Exception e) {
            log.error(e);
        }
    }

    public static ReportDatabaseDriver getDatabaseDriver() throws SQLException {
        return reportDatabaseDriver;
    }

    private static File getReceviedFileFromBluetooth(BluetoothServer bt) throws NoSuchElementException, FileNotFoundException {
        String newReceivedFileName = /*"exp-db.sql";*/bt.popReceiveFileName();
        String synchDataBaseFile = "base-synchronization";
        return (new File(synchDataBaseFile+"/"+newReceivedFileName) );
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

            try {
                ReportServer.getWebAction(WebActionType.WEB_ACTION_REFRESH_DETOUR_TABLE).complete();
            } catch (NullPointerException e) {

            }
        } catch (NoSuchElementException e1) {

        } catch (SQLSyntaxErrorException | FileNotFoundException e2) {
            log.error(e2);
        }
    }

    synchronized public static void putWebAction(WebActionType type, AsyncContext context) {
        webActions.put(type, context);
    }

    synchronized public static AsyncContext getWebAction(WebActionType type) throws NullPointerException {
        return webActions.get(type);
    }
}
