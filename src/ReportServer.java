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

import static java.lang.Thread.sleep;

public class ReportServer {

    private static final int versionMajor = 1;
    private static final int versionMinor = 0;
    private static final int versionBuild = 1;

    private static WebServer webServer;
    private static BluetoothServer bluetoothServer;
    private static DatabaseDriver databaseDriver;
    private static SqlCommandList sqlScript;

    private static final Logger log = Logger.getLogger(ReportServer.class);

    public static void printConsoleHelp()
    {
        //String ver = ReportServer.class.getPackage().getImplementationVersion();
        System.out.println("reportserver v"+versionMajor+"."+versionMinor+"build"+versionBuild+"\n"+
                           "Copyright (C) 2016 M&D, Inc.");

        //usage format:
        //Usage: reportserver [-aDde] [-f | -g] [-n number] [-b b_arg | -c c_arg] req1 req2 [opt1 [opt2]]
        System.out.println("Usage: reportserver port");
        System.out.println("\tport: web-server port number");
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        //PropertyConfigurator.configure("log4j.properties");
        log.info("Application status:\t\t[INIT]");

        String logMessage = "Web server init\t\t";

        try {

            try {
                int port = Integer.parseInt(args[0]);
                webServer = new WebServer(port, "webapp");
            } catch(Exception e) {
                System.out.println("Error: incorrect port number.");
                printConsoleHelp();
                return;
            }

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

        logMessage = "Application database driver\t\t";
        try {
            databaseDriver = new DatabaseDriver();
            databaseDriver.init(ProjectDirectories.commonDatabaseRelativePath,
                                ProjectDirectories.localDatabaseRelativePath);
            log.info(logMessage+"[OK]");
        } catch(Exception e) {
            log.info(logMessage+"[FAIL]");
            log.error(e);
            return;
        }

        log.info("Application status:\t\t[RUNNING]");

        while (true) {
            bluetoothTransactionHandler(bluetoothServer);
            sleep(500);
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

            if (BluetoothPacketType.REPLACE_DATABASE.getId() == type) {
                try {
                    bluetoothReplaceDatabaseTransactionHandler(bt, (BluetoothFileTransaction) newReceivedTransaction);
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

    private static void bluetoothReplaceDatabaseTransactionHandler(BluetoothServer bt, BluetoothFileTransaction transaction) {
        int status = BluetoothTransactionStatus.DONE.getId();

        JSONObject header = new JSONObject();
        header.put("type", new Long(BluetoothPacketType.RESPONSE.getId()));
        header.put("userId", transaction.getHeader().get("userId"));
        header.put("size", transaction.getHeader().get("size"));
        header.put("status", new Long(status));
        bt.sendData(new BluetoothSimpleTransaction(header));

        try {
            File dataBase = new File(ProjectDirectories.directoryDownloads + "/" + transaction.getFileName());
            databaseDriver.replaceCommonBase(dataBase);
            ReportServer.sendUserMessage("Установлена новая база");
        } catch (IOException e) {
            ReportServer.sendUserMessage("Ошибка установки базы. Новая база не применена.");
            log.error(e);
        }
    }

    private static void bluetoothBinaryFileTransactionHandler(BluetoothServer bt, BluetoothFileTransaction transaction) {

        File scriptFile = new File(ProjectDirectories.directoryDownloads + "/" + transaction.getFileName());

        int status = BluetoothTransactionStatus.DONE.getId();

        JSONObject header = new JSONObject();
        header.put("type",      new Long(BluetoothPacketType.RESPONSE.getId()));
        header.put("userId", transaction.getHeader().get("userId"));
        header.put("size", transaction.getHeader().get("size"));
        header.put("status",    new Long(status));
        bt.sendData(new BluetoothSimpleTransaction(header));

        ReportServer.sendUserMessage("Принят файл: "+transaction.getFileName());
    }

    private static void bluetoothSynchTransactionHandler(BluetoothServer bt, BluetoothSimpleTransaction transaction) {
        try {
            int clientVersion = databaseDriver.checkClientVersion(bt.getRemoteDeviceBluetoothAddress());
            int dbVersion = databaseDriver.getDatabaseVersion();
            int realClientVersion = (int)transaction.getHeader().get("version");

            sendUserMessage("Принят запрос на синхронизацию.");

            //Если версия базы в планшете некорректная
            if  ( (realClientVersion != clientVersion)   ||
                  (realClientVersion > dbVersion)        ||
                  (clientVersion > dbVersion) ) {
                sendUserMessage("Некорректная версия базы на планшете. Обновление базы.");

                //перебиваем версию
                databaseDriver.setClientVersion(bt.getRemoteDeviceBluetoothAddress(), 0);

                //передать файлик базы данных целиком
                try {
                    File databaseFile = new File(ProjectDirectories.directoryDatabase+"/app-data.db3");
                    if (databaseFile.exists()) {
                        JSONObject header = new JSONObject();
                        header.put("type", new Long(BluetoothPacketType.BINARY_FILE.getId()));
                        header.put("userId", transaction.getHeader().get("userId"));
                        header.put("size", databaseFile.length());
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
                header.put("userId", transaction.getHeader().get("userId"));
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
                        sourceHistory.addAll(databaseDriver.getClientHistory(currentVersion));
                    }

                    JSONObject header = new JSONObject();
                    header.put("type", new Long(BluetoothPacketType.SQL_QUERIES.getId()));
                    header.put("userId", transaction.getHeader().get("userId"));
                    header.put("size", transaction.getHeader().get("size"));

                    try {
                        File temp = File.createTempFile("client_history", ".tmp");
                        temp.deleteOnExit();

                        FileWriter writer = new FileWriter(temp);

                        writer.write(header.toJSONString());

                        Iterator it = sourceHistory.iterator();
                        while (it.hasNext()) {
                            writer.write(it.next() + ";");
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
                        File databaseFile = new File(ProjectDirectories.directoryDatabase+"/app-data.db3");
                        if (databaseFile.exists()) {
                            JSONObject header = new JSONObject();
                            header.put("type", new Long(BluetoothPacketType.BINARY_FILE.getId()));
                            header.put("userId", transaction.getHeader().get("userId"));
                            header.put("size", databaseFile.length());
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
        File scriptFile = new File(ProjectDirectories.directoryDownloads + "/" + transaction.getFileName());
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
        header.put("userId", transaction.getHeader().get("userId"));
        header.put("size", transaction.getHeader().get("size"));
        header.put("status",    new Long(status));
        bt.sendData(new BluetoothSimpleTransaction(header));

        databaseDriver.backupCurrentDatabase(Integer.toString(databaseDriver.getDatabaseVersion()));

        long userId = (long)(transaction.getHeader().get("userId"));

        databaseDriver.runScript((int)userId, sqlScript);

        try {
            AsyncContext asyncRefreshDetourTable = WebServer.popWebAction(WebActionType.REFRESH_DETOUR_TABLE);
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

    public static DatabaseDriver getDatabaseDriver() {
        return databaseDriver;
    }

    public static void sendUserMessage(String text) {
        try {
            Date currentDate = new Date();
            //Выбираем нужный web-сервер и отправляем ему текст
            webServer.sendUserMessage(currentDate, text);
            //отправляем в базу
            databaseDriver.addUserMessageToDatabase(currentDate, text);
        } catch (Exception e) {

        }
    }

}
