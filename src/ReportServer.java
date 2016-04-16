package reportserver;

import javax.servlet.AsyncContext;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.*;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import static java.lang.Thread.sleep;

public class ReportServer {

    private static final int versionMajor = 1;
    private static final int versionMinor = 0;
    private static final int versionBuild = 3;

    private static WebServer webServer;
    private static BluetoothServer bluetoothServer;
    private static DatabaseDriver databaseDriver;
    private static FeedbackUsersMessage userFeedback;
    private static LinkedList<BluetoothSimpleTransaction> groupTransaction = new LinkedList<BluetoothSimpleTransaction>();

    private static SqlCommandList sqlScript;
    private static CommonUserInterface userInterface;
    private static int currentConnectionId = 0;

    private static final Logger log = Logger.getLogger(ReportServer.class);

    private static void printConsoleHelp()
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

        String logMessage = "Application database driver\t\t";

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

        logMessage = "Web server init\t\t";

        try {
            try {
                int port = Integer.parseInt(args[0]);
                userFeedback = new FeedbackUsersMessage(databaseDriver);
                webServer = new WebServer(port, "webapp", userFeedback);
            } catch(Exception e) {
                System.out.println("Error: incorrect port number.");
                printConsoleHelp();
                return;
            }

            webServer.init();
            log.info(logMessage+"[OK]");

            logMessage = "Bluetooth driver init\t\t";
            bluetoothServer = new BluetoothServer(userFeedback);
            bluetoothServer.init();
            log.info(logMessage+"[OK]");

            databaseDriver.initDatabaseVersion(bluetoothServer.getLocalHostMacAddress());

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
            sleep(500);
        }
    }

    private static void bluetoothTransactionHandler(BluetoothServer bt) throws FileNotFoundException {
        try {
            BluetoothSimpleTransaction newReceivedTransaction = bt.getFirstReceivedTransaction();
            long type = (long) newReceivedTransaction.getHeader().get("type");

            do {
                if (BluetoothPacketType.RESPONSE.getId() == type) {
                    if (!groupTransaction.isEmpty()) {
                        if (bt.sendData(groupTransaction.getFirst())) {
                            groupTransaction.remove();
                        }
                    }
                    break;
                }

                if (BluetoothPacketType.SYNCH_REQUEST.getId() == type) {
                    bluetoothSynchTransactionHandler(bt, newReceivedTransaction);
                    break;
                }

                if (BluetoothPacketType.SQL_QUERIES.getId() == type) {
                    try {
                        bluetoothSqlQueriesTransactionHandler(bt, (BluetoothFileTransaction) newReceivedTransaction);
                    } catch (ClassCastException e) {
                        log.warn(e);
                    }
                    break;
                }

                if (BluetoothPacketType.BINARY_FILE.getId() == type) {
                    try {
                        bluetoothBinaryFileTransactionHandler(bt, (BluetoothFileTransaction) newReceivedTransaction);
                    } catch (ClassCastException e) {
                        log.warn(e);
                    }
                    break;
                }

                if (BluetoothPacketType.REPLACE_DATABASE.getId() == type) {
                    try {
                        bluetoothReplaceDatabaseTransactionHandler(bt, (BluetoothFileTransaction) newReceivedTransaction);
                    } catch (ClassCastException e) {
                        log.warn(e);
                    }
                    break;
                }

                if (BluetoothPacketType.SESSION_CLOSE.getId() == type) {
                    bt.reopenNewConnection();
                    userFeedback.sendUserMessage("Текущее соедение завершено. Ожидаю нового подключения.");
                    break;
                }
            } while (false);

            //Если обработка произошла без исключений удаляем первую транзакцию из списка
            bt.removeFirstReceivedTransaction();

        } catch (NoSuchElementException e) {

        }
    }

    private static void bluetoothReplaceDatabaseTransactionHandler(BluetoothServer bt, BluetoothFileTransaction transaction) {
        int status = BluetoothTransactionStatus.DONE.getId();

        JSONObject header = new JSONObject();
        header.put("type", BluetoothPacketType.RESPONSE.getId());
        header.put("userId", transaction.getHeader().get("userId"));
        header.put("size", transaction.getHeader().get("size"));
        header.put("status", status);
        bt.sendData(new BluetoothSimpleTransaction(header));

        try {
            File dataBase = new File(ProjectDirectories.directoryDownloads + "/" + transaction.getFileName());
            databaseDriver.replaceCommonBase(dataBase);
            userFeedback.sendUserMessage("Установлена новая база");
        } catch (IOException e) {
            userFeedback.sendUserMessage("Ошибка установки базы. Новая база не применена.");
            log.error(e);
        }
    }

    private static void bluetoothBinaryFileTransactionHandler(BluetoothServer bt, BluetoothFileTransaction transaction) {

        File scriptFile = new File(ProjectDirectories.directoryDownloads + "/" + transaction.getFileName());

        int status = BluetoothTransactionStatus.DONE.getId();

        JSONObject header = new JSONObject();
        header.put("type", BluetoothPacketType.RESPONSE.getId());
        header.put("userId", transaction.getHeader().get("userId"));
        header.put("size", transaction.getHeader().get("size"));
        header.put("status", status);
        bt.sendData(new BluetoothSimpleTransaction(header));

        userFeedback.sendUserMessage("Принят файл: "+transaction.getFileName());
    }

    private static void bluetoothSynchTransactionHandler(BluetoothServer bt, BluetoothSimpleTransaction transaction) {
        try {
            int clientVersion = databaseDriver.checkClientVersion(bt.getRemoteDeviceBluetoothAddress());
            int dbVersion = databaseDriver.getDatabaseVersion();
            long realClientVersion = 0;

            if (transaction.getHeader().containsKey("version")) {
                realClientVersion = (long) transaction.getHeader().get("version");
            }
            else {
                return;
            }

            userFeedback.sendUserMessage("Принят запрос на синхронизацию.");

            //версия актуальна, синхронизировать нечего
            if ( (clientVersion == dbVersion) ) {
                //Делать ничего не нужно, просто отправляем SESSION_CLOSE
                JSONObject header = new JSONObject();
                header.put("type", BluetoothPacketType.SESSION_CLOSE.getId());
                bt.sendData(new BluetoothSimpleTransaction(header));
                userFeedback.sendUserMessage("База планшета актуальна.");
            } else {
                //Передаем всю положенную клиенту историю
                if (clientVersion < dbVersion) {
                    if (dbVersion - clientVersion < 10) {
                        groupTransaction = addSqlHistoryToGroupTransaction((int)transaction.getHeader().get("userId"),
                                                                            clientVersion, dbVersion, groupTransaction);

                        groupTransaction = addPicturesToGroupTransaction((int)transaction.getHeader().get("userId"),
                                                                            clientVersion, dbVersion, groupTransaction);

                        groupTransaction = addCloseSessionToGroupTransaction(groupTransaction);

                        sendGroupTransactions(bt, groupTransaction);

                        userFeedback.sendUserMessage("Данные для синхронизации отправлены.");
                    } else {
                        groupTransaction = addReplaceDatabaseToGroupTransaction((int)transaction.getHeader().get("userId"),
                                                                                dbVersion, groupTransaction);

                        groupTransaction = addPicturesToGroupTransaction((int)transaction.getHeader().get("userId"),
                                clientVersion, dbVersion, groupTransaction);

                        groupTransaction = addCloseSessionToGroupTransaction(groupTransaction);

                        sendGroupTransactions(bt, groupTransaction);

                        userFeedback.sendUserMessage("Версия устарела. Отправлена актуальная база.");
                    }
                } else {
                    //Если версия базы в планшете некорректная
                    userFeedback.sendUserMessage("Ошибка: конфликт версий. Обратитесь к администратору.");
                    //todo: возможна новая команда синхронизации для пользовательского планшета
                }
            }
        } catch (SQLException|IOException e) {
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

        if (transaction.getHeader().containsKey("userId")) {
            long userId = (long) (transaction.getHeader().get("userId"));

            boolean isAdmin = false;

            try {
                isAdmin = databaseDriver.isUserAdmin((int) userId);
            } catch (SQLException e) {
                userFeedback.sendUserMessage("Ошибка идентификации пользователя. Пользователь не найден.");
            }

            try {
                databaseDriver.backupCurrentDatabase(Integer.toString(databaseDriver.getDatabaseVersion()));
                databaseDriver.runScript(isAdmin, sqlScript);
            } catch (SQLException e) {
                userFeedback.sendUserMessage("Ошибка обработки SQL-запроса. Cинхронизация отменена.");
            }
        }

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

    static String getBluetoothMacAddress() {
        return bluetoothServer.getLocalHostMacAddress();
    }

    public static DatabaseDriver getDatabaseDriver() {
        return databaseDriver;
    }

    private static void sendGroupTransactions(BluetoothServer bt, LinkedList<BluetoothSimpleTransaction> groupTransaction_) {
        try {
            if (bt.sendData(groupTransaction_.getFirst())) {
                groupTransaction_.remove();
            }
        } catch (NoSuchElementException e) {

        }
    }

    private static LinkedList<BluetoothSimpleTransaction> addSqlHistoryToGroupTransaction(int userId,
                                                                                         int clientVersion_,
                                                                                         int dbVersion_,
                                                                                         LinkedList<BluetoothSimpleTransaction> groupTransaction_) {
        ArrayList<String> sourceHistory = new ArrayList<String>();
        try {
            for (int currentVersion = (clientVersion_ + 1); currentVersion <= dbVersion_; ++currentVersion) {
                sourceHistory.addAll(databaseDriver.getClientHistory(currentVersion));
            }
        } catch(SQLException e) {
            log.error(e);
            return groupTransaction_;
        }

        //Отправляем историю базы
        try {
            File temp = File.createTempFile("client_history", ".tmp");
            temp.deleteOnExit();

            try (FileOutputStream os = new FileOutputStream(temp)) {
                try (BufferedOutputStream writer = new BufferedOutputStream(os)) {

                    Iterator it = sourceHistory.iterator();
                    while (it.hasNext()) {
                        writer.write(new String(it.next() + ";").getBytes("UTF-8"));
                    }

                    writer.flush();
                    writer.close();
                }
            }

            JSONObject header = new JSONObject();
            header.put("type", new Long(BluetoothPacketType.SQL_QUERIES.getId()));
            header.put("userId", new Long(userId));
            header.put("size", temp.length());

            log.info(temp.getAbsolutePath());

            groupTransaction_.add(new BluetoothFileTransaction(header, temp.getAbsolutePath()));
        } catch (IOException e) {
            log.error(e);
        }

        return groupTransaction_;
    }

    private static LinkedList<BluetoothSimpleTransaction> addPicturesToGroupTransaction(int userId,
                                                                                       int clientVersion_,
                                                                                       int dbVersion_,
                                                                                       LinkedList<BluetoothSimpleTransaction> groupTransaction_) {
        ArrayList<String> picturesHistory = new ArrayList<String>();

        try {
            for (int currentVersion = (clientVersion_ + 1); currentVersion <= dbVersion_; ++currentVersion) {
                 picturesHistory.addAll(databaseDriver.getClientPicturesHistory(currentVersion));
        }
        } catch (SQLException e) {
            log.error(e);
            return groupTransaction_;
        }

        //Ставим в очередь передачу файликов
        if (!picturesHistory.isEmpty()) {
            for (Object currentFilename : picturesHistory) {
                Path path = Paths.get(ProjectDirectories.directoryDownloads + "/" + currentFilename);
                if (Files.exists(path)) {
                    JSONObject fileHeader = new JSONObject();
                    fileHeader.put("type", BluetoothPacketType.BINARY_FILE.getId());
                    fileHeader.put("userId", userId);
                    try {
                        fileHeader.put("size", Files.size(path));
                    } catch (IOException e) {
                        log.error(e);
                    }
                    fileHeader.put("filename", currentFilename);
                    groupTransaction_.add(new BluetoothFileTransaction(fileHeader, path.toAbsolutePath().toString()));
                }
            }
        }

        return groupTransaction_;
    }
    private static LinkedList<BluetoothSimpleTransaction> addReplaceDatabaseToGroupTransaction(int userId,
                                                                                              int dbVersion_,
                                                                                              LinkedList<BluetoothSimpleTransaction> groupTransaction_) {
        //передать файлик базы данных целиком
        File databaseFile = new File(ProjectDirectories.commonDatabaseRelativePath);
        if (databaseFile.exists()) {
            JSONObject header = new JSONObject();
            header.put("type", (BluetoothPacketType.REPLACE_DATABASE.getId()));
            header.put("userId", userId);
            header.put("size", databaseFile.length());
            header.put("version", dbVersion_);

            groupTransaction_.add(new BluetoothFileTransaction(header, databaseFile.getAbsolutePath()));
        } else {
            log.error("Database not exist !!!");
        }

        return groupTransaction_;
    }

    private static LinkedList<BluetoothSimpleTransaction> addCloseSessionToGroupTransaction(LinkedList<BluetoothSimpleTransaction> groupTransaction_) {
        //Ставим в группу для отправки закрытия сессии после получения последнего RESPONSE
        JSONObject sessionCloseHeader = new JSONObject();
        sessionCloseHeader.put("type", BluetoothPacketType.SESSION_CLOSE.getId());
        groupTransaction_.add(new BluetoothSimpleTransaction(sessionCloseHeader));

        return groupTransaction_;
    }
}
