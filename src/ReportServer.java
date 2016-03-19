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

        PropertyConfigurator.configure("log4j.properties");

        log.info("Application status:\t\t[INIT]");

        String logMessage = "Database driver\t\t";
        try {
            reportDatabaseDriver = new ReportDatabaseDriver();
            reportDatabaseDriver.init("base-synchronization/app-data.db3");
            log.info(logMessage+"[OK]");
        } catch(Exception e) {
            log.info(logMessage+"[FAIL]");
            log.error(e);
            return;
        }

        logMessage = "Web server init\t\t";
        try {
            webServer = new WebServer(8080, "webapp");
            webServer.init();
            log.info(logMessage+"[OK]");

            logMessage = "Bluetooth driver init\t\t";
            bluetoothServer = new BluetoothServer();
            bluetoothServer.init();
            log.info(logMessage+"[OK]");

            logMessage = "Web server start\t\t";
            webServer.start();
            log.info(logMessage+"[OK]");
        } catch(Exception e) {
            log.info(logMessage+"[FAIL]");
            log.error(e);
            return;
        }

        log.info("Application status:\t\t[RUNNING]");

        while (true) {
            bluetoothTransactionHandler(bluetoothServer);
        }
    }

    private static void bluetoothTransactionHandler(BluetoothServer bt) throws FileNotFoundException {
        try {
            BluetoothTransaction newReceivedTransaction = bt.popReceivedTransaction();

            long type = (long)newReceivedTransaction.getHeader().get("type");

            if (BluetoothPacketType.SQL_QUERIES.getId() == type) {
                bluetoothSqlQueriesTransactionHandler(bt, newReceivedTransaction);
            }

            if (BluetoothPacketType.SYNCH_REQUEST.getId() == type) {
                bluetoothSynchTransactionHandler(bt, newReceivedTransaction);
            }

            if (BluetoothPacketType.BINARY_FILE.getId() == type) {
                bluetoothBinaryTransactionHandler(bt, newReceivedTransaction);
            }
        } catch (NoSuchElementException e) {

        }
    }

    private static void bluetoothBinaryTransactionHandler(BluetoothServer bt, BluetoothTransaction transaction) {

    }

    private static void bluetoothSynchTransactionHandler(BluetoothServer bt, BluetoothTransaction transaction) {

    }

    private static void bluetoothSqlQueriesTransactionHandler(BluetoothServer bt, BluetoothTransaction transaction) {
        String synchDataBaseFile = "base-synchronization";
        File scriptFile = new File(synchDataBaseFile + "/" + transaction.getFileName());
        int status = 0;

        try {
            sqlScript = new SqlCommandList(scriptFile);
            status = BluetoothTransactionStatus.DONE.getId();
        } catch (SQLSyntaxErrorException e) {
            log.warn(e);
            status = BluetoothTransactionStatus.ERROR.getId();
        } catch (FileNotFoundException e) {
            log.error(e);
            status = BluetoothTransactionStatus.ERROR.getId();
            return;
        }

        JSONObject header = new JSONObject();
        header.put("type",      new Long(BluetoothPacketType.RESPONSE.getId()));
        header.put("userId",    (Long)(transaction.getHeader().get("userId")));
        header.put("size",      (Long)(transaction.getHeader().get("size")));
        header.put("status",    new Long(status));
        bt.sendData(new BluetoothTransaction(header));

        reportDatabaseDriver.BackupCurrentDatabase(Integer.toString(reportDatabaseDriver.getDbSynchId()));
        reportDatabaseDriver.RunScript(sqlScript);

        try {
            ReportServer.getWebAction(WebActionType.WEB_ACTION_REFRESH_DETOUR_TABLE).complete();
        } catch (NullPointerException e) {

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

    synchronized public static void putWebAction(WebActionType type, AsyncContext context) {
        webActions.put(type, context);
    }

    synchronized public static AsyncContext getWebAction(WebActionType type) throws NullPointerException {
        return webActions.get(type);
    }
}
