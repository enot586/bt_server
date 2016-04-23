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
    private static final int versionBuild = 4;

    private static WebServer webServer;
    private static BluetoothServer bluetoothServer;
    private static DatabaseDriver databaseDriver;
    private static FeedbackUsersMessage userFeedback;

    private static GroupTransaction groupTransaction;

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

    private static void bluetoothTransactionHandler(BluetoothServer bt) {

        try {
            if (!groupTransaction.isComplete()) {
                groupTransaction.handler();
            } else {
                groupTransaction = null;
            }
        } catch (NullPointerException e) {
            //групповая транзакция не создавалась, игнорируем
        }

        try {
            BluetoothSimpleTransaction newReceivedTransaction = bt.getFirstReceivedTransaction();

            if (!newReceivedTransaction.getHeader().containsKey("type")) {
                userFeedback.sendUserMessage("Ошибка: некорректный формат пакета.");
                return;
            }

            int type = ((Long)newReceivedTransaction.getHeader().get("type")).intValue();

            do {
                if (BluetoothPacketType.RESPONSE.getId() == type) {
                    try {
                        groupTransaction.responseHandler(bt);
                    } catch (NullPointerException e) {
                        //групповая транзакция не создавалсь, игнорируем
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

                if (BluetoothPacketType.END_TRANSACTION.getId() == type) {
                    userFeedback.sendUserMessage("Окончание текущей транзакции");
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

        try {
            databaseDriver.removeLocalHistory();
            databaseDriver.initDatabaseVersion(bt.getLocalHostMacAddress());
            databaseDriver.setClientVersion(bt.getRemoteDeviceBluetoothAddress(), 1);
        } catch (IOException|SQLException e) {
            userFeedback.sendUserMessage("Ошибка очистки истории базы данных.");
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

        //сверяем connectionId и если не совпадает увеличиваем версию,
        //если connectionId одинаковый, считаем все изменения проходят в рамках текущей версии
        boolean isNeedToIncrementDbVersion = (currentConnectionId != bt.getConnectionId());

        try {
            databaseDriver.addFileToHistory(scriptFile.toPath(), isNeedToIncrementDbVersion);
            databaseDriver.setClientVersion(bt.getRemoteDeviceBluetoothAddress(), databaseDriver.getDatabaseVersion());
        } catch (IOException|SQLException e) {
            log.error(e);
            userFeedback.sendUserMessage("Ошибка при обращении к базе даных.");
        }

        currentConnectionId = bt.getConnectionId();
    }

    private static void bluetoothSynchTransactionHandler(BluetoothServer bt, BluetoothSimpleTransaction transaction) {
        if (!transaction.getHeader().containsKey("userId") ||
            !transaction.getHeader().containsKey("version")) {
            userFeedback.sendUserMessage("Ошибка: Некорректный формат SYNCH_REQUEST.");
            return;
        }

        int userId = ((Long) transaction.getHeader().get("userId")).intValue();
        int realClientVersion = ((Long) transaction.getHeader().get("version")).intValue();

        int clientVersion = 0;
        try {
            clientVersion = databaseDriver.checkClientVersion(bt.getRemoteDeviceBluetoothAddress());
        } catch(IOException | SQLException e) {
            log.error(e);
            userFeedback.sendUserMessage("Ошибка: не могу установить версию базы клиента");
            return;
        }

        int dbVersion = databaseDriver.getDatabaseVersion();

        if (realClientVersion != clientVersion) {
            userFeedback.sendUserMessage("Конфликт: клиент потерял версионость.");
            groupTransaction = new GroupTransaction();

            try {
                groupTransaction.add(addReplaceDatabaseToGroupTransaction(userId, dbVersion));
                //groupTransaction.add(addEndTransactionToGroupTransaction());
                groupTransaction.setCallbacks(
                        new GroupTransactionCallback() {
                            @Override
                            public void success() {
                                //присваивать версию только если тразакция завершилась успешно
                                try {
                                    databaseDriver.setClientVersion(bt.getRemoteDeviceBluetoothAddress(),
                                            databaseDriver.getDatabaseVersion());
                                } catch (IOException | SQLException e) {
                                    log.error(e);
                                    userFeedback.sendUserMessage("Ошибка: не удалось инкрементировать весию БД.");
                                }
                            }

                            @Override
                            public void fail() {
                                log.warn("client RESPONSE fail");
                                userFeedback.sendUserMessage("Ошибка: не удалось получить ответ от клиента");
                            }
                        }
                );

                if (bt.sendData(groupTransaction)) {
                    userFeedback.sendUserMessage("Отправлена актуальная база.");
                } else {
                    userFeedback.sendUserMessage("Ошибка: Не могу отправить данные.");
                }
            } catch(FileNotFoundException e) {
                log.error("Database not exist !!!");
            }

            return;
        }

        //try {
            userFeedback.sendUserMessage("Принят запрос на синхронизацию.");

            if (clientVersion == 0) {
                //первое обращение клиента к серверу. Необходима полная синхронизация
                groupTransaction = new GroupTransaction();

                try {
                    groupTransaction.add(addReplaceDatabaseToGroupTransaction(userId, dbVersion));
                } catch(FileNotFoundException e) {
                    log.error("Database not exist !!!");
                }

                //необходимо передавать все картинки из таблицы pictures
                groupTransaction.addAll(addPicturesFromTableToGroupTransaction(userId));
                groupTransaction.add(addEndTransactionToGroupTransaction(dbVersion));
                groupTransaction.setCallbacks(
                    new GroupTransactionCallback() {
                        @Override
                        public void success() {
                            //присваивать версию только если тразакция завершилась успешно
                            try {
                                databaseDriver.setClientVersion(bt.getRemoteDeviceBluetoothAddress(),
                                        databaseDriver.getDatabaseVersion());
                            } catch (IOException | SQLException e) {
                                log.error(e);
                                userFeedback.sendUserMessage("Ошибка: не удалось инкрементировать весию БД.");
                            }
                        }

                        @Override
                        public void fail() {
                            log.warn("client RESPONSE fail");
                            userFeedback.sendUserMessage("Ошибка: не удалось получить ответ от клиента");
                        }
                    }
                );

                if (bt.sendData(groupTransaction)) {
                    userFeedback.sendUserMessage("Версия устарела. Отправлена актуальная база.");
                } else {
                    userFeedback.sendUserMessage("Ошибка: Не могу отправить данные.");
                }
            } else {
                if (clientVersion == dbVersion) {
                    //версия актуальна, синхронизировать нечего
                    //Делать ничего не нужно, просто отправляем SESSION_CLOSE
                    JSONObject header = new JSONObject();
                    header.put("type", BluetoothPacketType.END_TRANSACTION.getId());
                    header.put("version", dbVersion);
                    if(bt.sendData(new BluetoothSimpleTransaction(header))) {
                        userFeedback.sendUserMessage("База планшета актуальна.");
                    } else {
                        userFeedback.sendUserMessage("Ошибка: Не могу отправить данные.");
                    }
                } else if (clientVersion < dbVersion) {
                    //Передаем всю положенную клиенту историю
                    groupTransaction = new GroupTransaction();
                    groupTransaction.addAll(addSqlHistoryToGroupTransaction(userId, clientVersion, dbVersion));
                    groupTransaction.addAll(addPicturesToGroupTransaction(userId, clientVersion, dbVersion));
                    groupTransaction.add(addEndTransactionToGroupTransaction(dbVersion));

                    if (bt.sendData(groupTransaction)) {
                        userFeedback.sendUserMessage("Данные для синхронизации отправлены.");
                    } else {
                        userFeedback.sendUserMessage("Ошибка: Не могу отправить данные.");
                    }
                } else if (clientVersion > dbVersion) {
                    //Если версия базы в планшете некорректная
                    userFeedback.sendUserMessage("Ошибка: конфликт версий. Обратитесь к администратору.");
                }
            }
//        } catch (SQLException | IOException e) {
//            log.warn(e);
//        }
    }

    private static void bluetoothSqlQueriesTransactionHandler(BluetoothServer bt, BluetoothFileTransaction transaction) {
        if (!transaction.getHeader().containsKey("userId")) {
            userFeedback.sendUserMessage("Ошибка: Некорректный формат SQL_QUERIES.");
            return;
        }

        int userId = ((Long)transaction.getHeader().get("userId")).intValue();

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
        header.put("type", BluetoothPacketType.RESPONSE.getId());
        header.put("userId", userId);
        header.put("size", transaction.getHeader().get("size"));
        header.put("status", status);
        bt.sendData(new BluetoothSimpleTransaction(header));

        boolean isAdmin = false;

        try {
            isAdmin = databaseDriver.isUserAdmin(userId);
        } catch (SQLException e) {
            userFeedback.sendUserMessage("Ошибка идентификации пользователя. Пользователь не найден.");
        }

        try {
            databaseDriver.backupCurrentDatabase(Integer.toString(databaseDriver.getDatabaseVersion()));

            //сверяем connectionId и если не совпадает увеличиваем версию,
            //если connectionId одинаковый, считаем все изменения проходят в рамках текущей версии
            boolean isNeedToIncrementDbVersion = (currentConnectionId != bt.getConnectionId());
            databaseDriver.runScript(isAdmin, bt.getRemoteDeviceBluetoothAddress(), sqlScript, isNeedToIncrementDbVersion);

            currentConnectionId = bt.getConnectionId();
        } catch (IOException|SQLException e) {
            userFeedback.sendUserMessage("Ошибка обработки SQL-запроса. Cинхронизация отменена.");
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

    public static String getBluetoothMacAddress() {
        return bluetoothServer.getLocalHostMacAddress();
    }

    public static DatabaseDriver getDatabaseDriver() {
        return databaseDriver;
    }

    private static LinkedList<BluetoothSimpleTransaction> addSqlHistoryToGroupTransaction(long userId,
                                                                                         int clientVersion_,
                                                                                         int dbVersion_) {
        LinkedList<BluetoothSimpleTransaction> result = new LinkedList<BluetoothSimpleTransaction>();
        ArrayList<String> sourceHistory = new ArrayList<String>();
        try {
            for (int currentVersion = (clientVersion_ + 1); currentVersion <= dbVersion_; ++currentVersion) {
                sourceHistory.addAll(databaseDriver.getClientHistory(currentVersion));
            }
        } catch(SQLException e) {
            log.error(e);
            return result;
        }

        if (sourceHistory.isEmpty())
            return result;

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
            header.put("userId", userId);
            header.put("size", temp.length());
            header.put("version", dbVersion_);
            log.info(temp.getAbsolutePath());

            result.add(new BluetoothFileTransaction(header, temp.getAbsolutePath()));
        } catch (IOException e) {
            log.error(e);
        }

        return result;
    }

    private static LinkedList<BluetoothSimpleTransaction> addPicturesToGroupTransaction(long userId,
                                                                                       int clientVersion_,
                                                                                       int dbVersion_) {
        LinkedList<BluetoothSimpleTransaction> result = new LinkedList<BluetoothSimpleTransaction>();
        ArrayList<String> picturesHistory = new ArrayList<String>();

        try {
            for (int currentVersion = (clientVersion_ + 1); currentVersion <= dbVersion_; ++currentVersion) {
                picturesHistory.addAll(databaseDriver.getClientPicturesHistory(currentVersion));
            }
        } catch (SQLException e) {
            log.error(e);
            return result;
        }

        if (picturesHistory.isEmpty())
            return result;

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
                    result.add(new BluetoothFileTransaction(fileHeader, path.toAbsolutePath().toString()));
                }
            }
        }
    return result;
}

    private static BluetoothFileTransaction addReplaceDatabaseToGroupTransaction(long userId, int dbVersion_) throws FileNotFoundException {
        //передать файлик базы данных целиком
        File databaseFile = new File(ProjectDirectories.commonDatabaseRelativePath);
        if (databaseFile.exists()) {
            JSONObject header = new JSONObject();
            header.put("type", (BluetoothPacketType.REPLACE_DATABASE.getId()));
            header.put("userId", userId);
            header.put("size", databaseFile.length());
            header.put("version", dbVersion_);

            return new BluetoothFileTransaction(header, databaseFile.getAbsolutePath());
        }
        throw new FileNotFoundException();
    }

    private static BluetoothSimpleTransaction addEndTransactionToGroupTransaction(int versionDb_) {
        //Ставим в группу для отправки закрытия сессии после получения последнего RESPONSE
        JSONObject sessionCloseHeader = new JSONObject();
        sessionCloseHeader.put("type", BluetoothPacketType.END_TRANSACTION.getId());
        sessionCloseHeader.put("version", versionDb_);
        return new BluetoothSimpleTransaction(sessionCloseHeader);
    }

    private static LinkedList<BluetoothSimpleTransaction> addPicturesFromTableToGroupTransaction(int userId) {
        LinkedList<BluetoothSimpleTransaction> result = new LinkedList<BluetoothSimpleTransaction>();
        ArrayList<String> pictures = new ArrayList<String>();

        pictures.addAll(databaseDriver.getPictures());

        //Ставим в очередь передачу файликов
        if (!pictures.isEmpty()) {
            for (Object currentFilename : pictures) {
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
                    result.add(new BluetoothFileTransaction(fileHeader, path.toAbsolutePath().toString()));
                }
            }
        }
        return result;
    }


}
