package reportserver;

import javax.servlet.AsyncContext;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.simple.JSONObject;

public class ReportServer {

    private static WebServer webServer;
    private static BluetoothServer bluetoothServer;
    private static ReportDatabaseDriver reportDatabaseDriver;
    private static SqlCommandList sqlScript;
    private static final LinkedList<String> userMessages = new LinkedList<String>();
    private static Map< WebActionType, AsyncContext > webActions = new HashMap< WebActionType, AsyncContext >();
    private static final Logger log = Logger.getLogger(ReportServer.class);

    public static void main(String[] args) throws IOException, InterruptedException {

        PropertyConfigurator.configure("log4j.properties");

        log.info("Application status:\t\t[INIT]");

        String logMessage = "Web server init\t\t";
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

        logMessage = "Database driver\t\t";
        try {
            reportDatabaseDriver = new ReportDatabaseDriver();
            reportDatabaseDriver.init("base-synchronization/app-data.db3");
            log.info(logMessage+"[OK]");

            reportDatabaseDriver.initDatabaseVersion(getBluetoothMacAddress());
        } catch(Exception e) {
            log.info(logMessage+"[FAIL]");
            log.error(e);
            return;
        }

        log.info("Application status:\t\t[RUNNING]");

        while (true) {
            bluetoothTransactionHandler(bluetoothServer);

            userMessageHandler();
        }
    }

    private static void bluetoothTransactionHandler(BluetoothServer bt) throws FileNotFoundException {
        try {
            BluetoothSimpleTransaction newReceivedTransaction = bt.popReceivedTransaction();

            long type = (long) newReceivedTransaction.getHeader().get("type");

            if (BluetoothPacketType.SYNCH_REQUEST.getId() == type) {
                bluetoothSynchTransactionHandler(bt, newReceivedTransaction);
                return;
            }

            if (BluetoothPacketType.SQL_QUERIES.getId() == type) {
                try {
                    bluetoothSqlQueriesTransactionHandler(bt, (BluetoothFileTransaction) newReceivedTransaction);
                } catch (ClassCastException e) {
                    log.warn(e);
                }
                return;
            }

            if (BluetoothPacketType.BINARY_FILE.getId() == type) {
                try {
                    bluetoothBinaryTransactionHandler(bt, (BluetoothByteTransaction) newReceivedTransaction);
                } catch(ClassCastException e){
                    log.warn(e);
                }
                return;
            }
        } catch (NoSuchElementException e) {

        }
    }

    private static void bluetoothBinaryTransactionHandler(BluetoothServer bt, BluetoothByteTransaction transaction) {

    }

    private static void bluetoothSynchTransactionHandler(BluetoothServer bt, BluetoothSimpleTransaction transaction) {
        try {
            int clientVersion = reportDatabaseDriver.checkClientVersion(bt.getRemoteDeviceBluetoothAddress());
            int dbVersion = reportDatabaseDriver.getDatabaseVersion();

            if ( clientVersion < dbVersion ) {
                if (dbVersion - clientVersion < 10) {
                    ArrayList<String> sourceHistory = new ArrayList<String>();

                    for (int currentVersion = (clientVersion + 1); currentVersion < dbVersion; ++currentVersion) {
                        sourceHistory.addAll(reportDatabaseDriver.GetClientHistory(currentVersion));
                    }

                    JSONObject header = new JSONObject();
                    header.put("type", new Long(BluetoothPacketType.SQL_QUERIES.getId()));
                    header.put("userId", (Long) (transaction.getHeader().get("userId")));
                    header.put("size", (Long) (transaction.getHeader().get("size")));

                    try {
                        File temp = File.createTempFile("client_history", ".tmp");
                        temp.deleteOnExit();

                        FileWriter writer = new FileWriter(temp);

                        writer.write(header.toJSONString());

                        Iterator it = sourceHistory.iterator();
                        while (it.hasNext()) {
                            writer.write((String) it.next() + ";");
                        }

                        bt.sendData(new BluetoothFileTransaction(header, temp.getAbsolutePath()));
                    } catch (IOException e) {
                        log.error(e);
                    }
                }
                else {
                    //TODO: передать файлик
                }
            } else {
                //TODO: перебиваем версию на актуальную и шлем все изменения от 0
            }
        } catch (SQLException e) {
            log.warn(e);
        } catch (IOException e) {
            log.warn(e);
        }
    }

    private static void bluetoothSqlQueriesTransactionHandler(BluetoothServer bt, BluetoothFileTransaction transaction) {
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
        bt.sendData(new BluetoothSimpleTransaction(header));

        reportDatabaseDriver.BackupCurrentDatabase(Integer.toString(reportDatabaseDriver.getDatabaseVersion()));

        long userId = (long)(transaction.getHeader().get("userId"));
        reportDatabaseDriver.RunScript((int)userId, sqlScript);

        try {
            ReportServer.getWebAction(WebActionType.REFRESH_DETOUR_TABLE).complete();
        } catch (NullPointerException e) {
            //по каким-то причинам ajax соединение установлено не было
            log.warn(e);
        }
    }

    static CommonServer.ServerState getStateBluetoothServer() {
        return bluetoothServer.getServerState();
    }

    static void bluetoothServerStart() throws Exception {
        try {
            bluetoothServer.start();
        } catch(Exception e) {
            log.error(e);
        }
    }

    static void bluetoothServerStop() throws Exception {
        try {
            bluetoothServer.stop();
        } catch(Exception e) {
            log.error(e);
        }
    }

    public static String getBluetoothMacAddress() {
        return bluetoothServer.getLocalHostMacAddress();
    }

    public static ReportDatabaseDriver getDatabaseDriver() throws SQLException {
        return reportDatabaseDriver;
    }

    public static void sendUserMessage(String text) {
        try {
            reportDatabaseDriver.addUserMessageToDatabase(new Date(), text);
            synchronized (userMessages) {
                userMessages.add(text);
            }
        } catch (Exception e) {

        }
    }

    public synchronized static String popUserMessage() throws NoSuchElementException {
        String text;
        text = userMessages.peek();
        if (text == null)  throw new NoSuchElementException();
        userMessages.remove();
        return text;
    }

    static void userMessageHandler() {
        try {
            String text = popUserMessage();
            ReportServer.getWebAction(WebActionType.SEND_USER_MESSAGE).getResponse().getWriter().write(new String(text.getBytes("UTF8")));
            ReportServer.getWebAction(WebActionType.SEND_USER_MESSAGE).complete();
        } catch (NullPointerException | NoSuchElementException e) {

        } catch (IOException e) {
            log.warn(e);
        }
    }

    synchronized static void putWebAction(WebActionType type, AsyncContext context) {
        webActions.put(type, context);
    }

    synchronized public static AsyncContext getWebAction(WebActionType type) throws NullPointerException {
        return webActions.get(type);
    }

}
