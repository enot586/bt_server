package reportserver;

import javax.servlet.AsyncContext;
import javax.servlet.ServletResponse;
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
                    bluetoothBinaryFileTransactionHandler(bt, (BluetoothFileTransaction) newReceivedTransaction);
                } catch(ClassCastException e){
                    log.warn(e);
                }
                return;
            }

            if (BluetoothPacketType.SESSION_CLOSE.getId() == type) {
                sendUserMessage("Текущее соедение завершено. Ожидаю нового подключения.");
            }
        } catch (NoSuchElementException e) {

        }
    }

    private static void bluetoothBinaryFileTransactionHandler(BluetoothServer bt, BluetoothFileTransaction transaction) {
        String synchDataBaseFile = "base-synchronization";
        File scriptFile = new File(synchDataBaseFile + "/" + transaction.getFileName());

        int status = BluetoothTransactionStatus.DONE.getId();

        JSONObject header = new JSONObject();
        header.put("type",      new Long(BluetoothPacketType.RESPONSE.getId()));
        header.put("userId",    (Long)(transaction.getHeader().get("userId")));
        header.put("size",      (Long)(transaction.getHeader().get("size")));
        header.put("status",    new Long(status));
        bt.sendData(new BluetoothSimpleTransaction(header));

        ReportServer.sendUserMessage("Принят файл: "+transaction.getFileName());
    }

    private static void bluetoothSynchTransactionHandler(BluetoothServer bt, BluetoothSimpleTransaction transaction) {
        try {
            int clientVersion = reportDatabaseDriver.checkClientVersion(bt.getRemoteDeviceBluetoothAddress());
            int dbVersion = reportDatabaseDriver.getDatabaseVersion();
            int realClientVersion = (int)transaction.getHeader().get("version");

            sendUserMessage("Принят запрос на синхронизацию.");

            //Если версия базы в планшете некорректная
            if  ( (realClientVersion != clientVersion)   ||
                  (realClientVersion > dbVersion)        ||
                  (clientVersion > dbVersion) ) {
                sendUserMessage("Некорректная версия базы на планшете. Обновление базы.");

                //перебиваем версию
                reportDatabaseDriver.setClientVersion(bt.getRemoteDeviceBluetoothAddress(), 0);

                //передать файлик базы данных целиком
                try {
                    File databaseFile = new File("base-synchronization/app-data.db3");
                    if (databaseFile.exists()) {
                        JSONObject header = new JSONObject();
                        header.put("type", new Long(BluetoothPacketType.BINARY_FILE.getId()));
                        header.put("userId", (Long) (transaction.getHeader().get("userId")));
                        header.put("size", (Long) databaseFile.length());
                        header.put("filename", "app-data.db3");

                        bt.sendData(new BluetoothFileTransaction(header, databaseFile.getAbsolutePath()));
                    } else {
                        log.error("Database not exist !!!");
                    }
                } catch (SecurityException e) {
                    log.warn(e);
                }

                //FIXME: как-то скинуть версию базы для планшета?

                return;
            }

            //FIXME: (clientVersion == dbVersion == 0) непонятно что делать

            //версия актуальна, синхронизировать нечего
            if ( (dbVersion > 0) && (clientVersion == dbVersion) ) {
                JSONObject header = new JSONObject();
                header.put("type", new Long(BluetoothPacketType.RESPONSE.getId()));
                header.put("userId", (Long) (transaction.getHeader().get("userId")));
                header.put("status", new Long(BluetoothTransactionStatus.DONE.getId()));
                header.put("size", new Long(0));

                bt.sendData(new BluetoothSimpleTransaction(header));
                sendUserMessage("База планшета актуальна.");
                return;
            }

            //Передаем всю положенную клиенту историю
            if ( clientVersion < dbVersion ) {
                if (dbVersion - clientVersion < 10) {
                    ArrayList<String> sourceHistory = new ArrayList<String>();

                    for (int currentVersion = (clientVersion + 1); currentVersion < dbVersion; ++currentVersion) {
                        sourceHistory.addAll(reportDatabaseDriver.getClientHistory(currentVersion));
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
                        sendUserMessage("Данные для синхронизации отправлены.");
                    } catch (IOException e) {
                        log.error(e);
                    }
                }
                else {
                    //передать файлик базы данных целиком
                    try {
                        File databaseFile = new File("base-synchronization/app-data.db3");
                        if (databaseFile.exists()) {
                            JSONObject header = new JSONObject();
                            header.put("type", new Long(BluetoothPacketType.BINARY_FILE.getId()));
                            header.put("userId", (Long) (transaction.getHeader().get("userId")));
                            header.put("size", (Long) databaseFile.length());
                            header.put("filename", "app-data.db3");

                            bt.sendData(new BluetoothFileTransaction(header, databaseFile.getAbsolutePath()));
                            sendUserMessage("Версия устарела. Отправлена актуальная база.");
                        } else {
                            log.error("Database not exist !!!");
                        }
                    } catch (SecurityException e) {
                        log.warn(e);
                    }
                }
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

        reportDatabaseDriver.backupCurrentDatabase(Integer.toString(reportDatabaseDriver.getDatabaseVersion()));

        long userId = (long)(transaction.getHeader().get("userId"));

        reportDatabaseDriver.runScript((int)userId, sqlScript);

        try {
            AsyncContext asyncRefreshDetourTable = ReportServer.getWebAction(WebActionType.REFRESH_DETOUR_TABLE);
            asyncRefreshDetourTable.complete();
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

    public static ReportDatabaseDriver getDatabaseDriver() {
        return reportDatabaseDriver;
    }

    public static void sendUserMessage(String text) {
        try {
            //TODO: для пользователя передавать дату с сервера !!!
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
            AsyncContext asyncRequest = ReportServer.getWebAction(WebActionType.SEND_USER_MESSAGE);
            ServletResponse response = asyncRequest.getResponse();
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(new String(text.getBytes("UTF-8")));
            response.getWriter().flush();
            asyncRequest.complete();
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
