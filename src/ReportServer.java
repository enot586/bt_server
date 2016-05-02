package reportserver;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.LocalDevice;
import javax.servlet.AsyncContext;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import static java.lang.Thread.sleep;

public class ReportServer {

    private static final int versionMajor = 1;
    private static final int versionMinor = 0;
    private static final int versionBuild = 8;

    private static WebServer webServer;
    private static BluetoothServer bluetoothServer;
    private static DatabaseDriver databaseDriver;
    private static FeedbackUsersMessage userFeedback;

    private static GroupTransaction groupTransaction;

    private static SqlCommandList sqlScript;
    private static int currentConnectionId = 0;
    private static GroupTransactionHandler bluetoothGroupTransactionHandler;

    private static final Logger log = Logger.getLogger(ReportServer.class);

    private static void printConsoleHelp()  {
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

        bluetoothGroupTransactionHandler = new GroupTransactionHandler(bluetoothServer);

        //Bluetooth-сервер запускается при старте
        try {
            bluetoothServerStart();
            log.info("Bluetooth status:\t\t[RUNNING]");
        } catch (Exception e) {
            log.error(e);
            userFeedback.sendUserMessage("Ошибка: не удалось запустить bluetooth.");
            log.info("Bluetooth status:\t\t[STOPED]");
        }

        log.info("Application status:\t\t[RUNNING]");

        while (true) {
            bluetoothTransactionHandler(bluetoothServer);
            sleep(500);
        }
    }

    private static void bluetoothTransactionHandler(BluetoothServer bt) {
        if (groupTransaction != null) {
            bluetoothGroupTransactionHandler.timerHandler(groupTransaction);
            if (groupTransaction.isComplete()) {
                groupTransaction = null;
            }
        }

        try {
            SimpleTransaction newReceivedTransaction = bt.getFirstReceivedTransaction();

            if (!newReceivedTransaction.getHeader().containsKey("type")) {
                userFeedback.sendUserMessage("Ошибка: некорректный формат пакета.");
                return;
            }

            int type = ((Long)newReceivedTransaction.getHeader().get("type")).intValue();

            do {
                if (BluetoothPacketType.RESPONSE.getId() == type) {
                    if (groupTransaction != null) {
                        bluetoothGroupTransactionHandler.responsePacketHandler(groupTransaction);
                    }
                    break;
                }

                if (BluetoothPacketType.SYNCH_REQUEST.getId() == type) {
                    bluetoothSynchTransactionHandler(bt, newReceivedTransaction);
                    break;
                }

                if (BluetoothPacketType.SQL_QUERIES.getId() == type) {
                    try {
                        bluetoothSqlQueriesTransactionHandler(bt, (FileTransaction) newReceivedTransaction);
                    } catch (ClassCastException e) {
                        log.warn(e);
                    }
                    break;
                }

                if (BluetoothPacketType.BINARY_FILE.getId() == type) {
                    try {
                        bluetoothBinaryFileTransactionHandler(bt, (FileTransaction) newReceivedTransaction);
                    } catch (ClassCastException e) {
                        log.warn(e);
                    }
                    break;
                }

                if (BluetoothPacketType.REPLACE_DATABASE.getId() == type) {
                    try {
                        bluetoothReplaceDatabaseTransactionHandler(bt, (FileTransaction) newReceivedTransaction);
                    } catch (ClassCastException e) {
                        log.warn(e);
                    }
                    break;
                }

                if (BluetoothPacketType.END_TRANSACTION.getId() == type) {
                    /**
                     * Осуществляем синхронизацию версии на планшете.
                     * После передачи пачки обновлений на сервер клиент передает END_TRANSACTION.
                     * Мы закрываем сессию передавая актуальную версию БД, которая перебивается на планшете.
                     * Тем самым происходит синхронизация версий при обновлении.
                     */
                    int dbVersion = databaseDriver.getDatabaseVersion();
                    JSONObject header = new JSONObject();
                    header.put("type", new Long(BluetoothPacketType.SESSION_CLOSE.getId()));
                    header.put("version", new Long(dbVersion));
                    bt.sendData(new SimpleTransaction(header));
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

    private static void bluetoothReplaceDatabaseTransactionHandler(BluetoothServer bt, FileTransaction transaction) {
        int status = BluetoothTransactionStatus.DONE.getId();

        JSONObject header = new JSONObject();
        header.put("type", new Long(BluetoothPacketType.RESPONSE.getId()) );
        header.put("userId", (Long)transaction.getHeader().get("userId"));
        header.put("size", (Long)transaction.getHeader().get("size"));
        header.put("status", new Long(status));
        bt.sendData(new SimpleTransaction(header));

        try {
            File dataBase = new File(ProjectDirectories.directoryDownloads + "/" + transaction.getFileName());
            databaseDriver.replaceCommonBase(dataBase);
            userFeedback.sendUserMessage("Установлена новая база");
        } catch (IOException e) {
            userFeedback.sendUserMessage("Ошибка установки базы. Новая база не применена.");
            log.error(e);
        }

        /**
         * При подмене базы данных важно соблюдать очередность рассылки!
         * Сначала клиент должен отправлять базу, чтобы текущая версия БД установилась =1,
         * а затем отправлять файлы. Чтобы привязать их к текущей версии.
         * Обратный порядок приведет к некорректной записи версии для файлов.
         * Т.к. при замене базы текущая версия БД сбрасывается в 1.
         */
        try {
            databaseDriver.removeLocalHistory();
            databaseDriver.initDatabaseVersion(bt.getLocalHostMacAddress());
            databaseDriver.setClientVersion(bt.getRemoteDeviceBluetoothAddress(), 1);

            //чтобы исключить инкремент версии при получении файлов, если они будут отосланы
            currentConnectionId = bt.getConnectionId();
        } catch (IOException|SQLException e) {
            userFeedback.sendUserMessage("Ошибка очистки истории базы данных.");
            log.error(e);
        }

        //обновить таблицу у веб-морды
        try {
            AsyncContext asyncRefreshDetourTable = WebServer.popWebAction(WebActionType.REFRESH_DETOUR_TABLE);
            asyncRefreshDetourTable.complete();
        } catch (NullPointerException e) {
            //по каким-то причинам ajax соединение установлено не было
            log.warn(e);
        }
    }

    private static void bluetoothBinaryFileTransactionHandler(BluetoothServer bt, FileTransaction transaction) {

        File scriptFile = new File(ProjectDirectories.directoryDownloads + "/" + transaction.getFileName());

        int status = BluetoothTransactionStatus.DONE.getId();

        JSONObject header = new JSONObject();
        header.put("type", new Long(BluetoothPacketType.RESPONSE.getId()) );
        header.put("userId", (Long)transaction.getHeader().get("userId"));
        header.put("size", (Long)transaction.getHeader().get("size"));
        header.put("status", new Long(status));
        bt.sendData(new SimpleTransaction(header));

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

    private static void bluetoothSynchTransactionHandler(BluetoothServer bt, SimpleTransaction transaction) {
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
            log.error("Ошибка: не могу установить версию базы клиента");
            userFeedback.sendUserMessage("Ошибка: не могу установить версию базы клиента");
            return;
        }

        int dbVersion = databaseDriver.getDatabaseVersion();

        /**
         * Обнаружено несоответствие версии клиента в планшете и версии клиента в таблице сервера.
         * Считается, что клиент утратил версионность, то есть либо была подмена базы на плашете,
         * либо на сервере.
         * В результате - текущая весрия на планшете не является валидной, поэтому перезаписываем базу плашету.
         *
         * note:
         * Остается дыра: если на другой базе планшет имел такой же номер версии, что и на текущей,
         * то конфликт останется не замеченным!!!
         */
        if (realClientVersion != clientVersion) {
            userFeedback.sendUserMessage("Конфликт: клиент потерял версионность.");
            groupTransaction = new GroupTransaction();

            try {
                groupTransaction.add(addReplaceDatabaseToGroupTransaction(userId, dbVersion));
                groupTransaction.addAll(addPicturesFromTableToGroupTransaction(userId));
                groupTransaction.add(addSessionCloseToGroupTransaction(dbVersion));
                groupTransaction.setCallbacks(
                    new GroupTransactionCallback() {
                        @Override
                        public void success() {
                            //присваивать версию только если тразакция завершилась успешно
                            try {
                                databaseDriver.setClientVersion(bt.getRemoteDeviceBluetoothAddress(), dbVersion);
                            } catch (IOException | SQLException e) {
                                log.error(e);
                                userFeedback.sendUserMessage("Ошибка: не удалось инкрементировать весию БД для клиента.");
                            }
                        }

                        @Override
                        public void fail() {
                            bt.reopenNewConnection();
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
                userFeedback.sendUserMessage("Ошибка: Отсутствует БД.");
            }

            return;
        }

        userFeedback.sendUserMessage("Принят запрос на синхронизацию.");

        if (clientVersion == 0) {
            /**
             * Первое обращение клиента к серверу. Необходима полная синхронизация.
             * Если планшет с конкретным mac-адресом ни разу не устанавливал соединение с сервером,
             * то это выявляется по отсутствию клиента в таблице clients_version в local-data.db3
             * Новый клиент добавляется с версией =0.
             */
            groupTransaction = new GroupTransaction();

            try {
                groupTransaction.add(addReplaceDatabaseToGroupTransaction(userId, dbVersion));
            } catch(FileNotFoundException e) {
                log.error("Database not exist !!!");
                userFeedback.sendUserMessage("Ошибка: Отсутствует БД.");
            }

            //необходимо передавать все картинки из таблицы pictures.
            groupTransaction.addAll(addPicturesFromTableToGroupTransaction(userId));
            groupTransaction.add(addSessionCloseToGroupTransaction(dbVersion));
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
                        bt.reopenNewConnection();
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
            /**
             * Соединение установленно с клиентом у которого версия БД валидна.
             * Выясняем какой уровень синхронизации ему необходим.
             */
            if (clientVersion == dbVersion) {
                /**
                 * версия актуальна, синхронизировать нечего.
                 * Делать ничего не нужно, просто отправляем END_TRANSACTION.
                 */
                JSONObject header = new JSONObject();
                header.put("type", new Long(BluetoothPacketType.END_TRANSACTION.getId()));
                header.put("version", new Long(dbVersion));
                if(bt.sendData(new SimpleTransaction(header))) {
                    userFeedback.sendUserMessage("База планшета актуальна.");
                } else {
                    userFeedback.sendUserMessage("Ошибка: Не могу отправить данные.");
                }
            } else if (clientVersion < dbVersion) {
                //Передаем всю положенную клиенту историю.
                groupTransaction = new GroupTransaction();
                groupTransaction.addAll(addSqlHistoryToGroupTransaction(userId, clientVersion, dbVersion));
                groupTransaction.addAll(addPicturesToGroupTransaction(userId, clientVersion, dbVersion));
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
                            bt.reopenNewConnection();
                            log.warn("client RESPONSE fail");
                            userFeedback.sendUserMessage("Ошибка: не удалось получить ответ от клиента");
                        }
                    }
                );
                if (bt.sendData(groupTransaction)) {
                    userFeedback.sendUserMessage("Данные для синхронизации отправлены.");
                } else {
                    userFeedback.sendUserMessage("Ошибка: Не могу отправить данные.");
                }
            } else if (clientVersion > dbVersion) {
                //Если версия базы в планшете некорректная.
                userFeedback.sendUserMessage("Ошибка: конфликт версий. Обратитесь к администратору.");
            }
        }
    }

    private static void bluetoothSqlQueriesTransactionHandler(BluetoothServer bt, FileTransaction transaction) {
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
            status = BluetoothTransactionStatus.ERROR.getId();
            log.error(e);
            log.error("Ошибка: Не могу получить доступ к файлу "+transaction.getFileName());
            userFeedback.sendUserMessage("Ошибка: Не могу получить доступ к файлу "+transaction.getFileName());
            return;
        }

        JSONObject header = new JSONObject();
        header.put("type", new Long(BluetoothPacketType.RESPONSE.getId()) );
        header.put("userId", new Long(userId));
        header.put("size", (Long)transaction.getHeader().get("size"));
        header.put("status", new Long(status));
        bt.sendData(new SimpleTransaction(header));

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

        //обновить таблицу у веб-морды
        try {
            AsyncContext asyncRefreshDetourTable = WebServer.popWebAction(WebActionType.REFRESH_DETOUR_TABLE);
            asyncRefreshDetourTable.complete();
        } catch (NullPointerException e) {
            //по каким-то причинам ajax соединение установлено не было
            log.warn(e);
        }
    }

    public static CommonServer.ServerState getStateBluetoothServer() {
        return bluetoothServer.getServerState();
    }

    public static void bluetoothServerStart() throws Exception {
        LocalDevice bluetoothLocalDevice = LocalDevice.getLocalDevice();
        bluetoothServer.start();
    }

    public static void bluetoothServerStop() throws Exception {
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

    private static LinkedList<SimpleTransaction> addSqlHistoryToGroupTransaction(long userId,
                                                                                 int clientVersion_,
                                                                                 int dbVersion_) {
        LinkedList<SimpleTransaction> result = new LinkedList<SimpleTransaction>();
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
            header.put("userId", new Long(userId));
            header.put("size", new Long(temp.length()));
            header.put("version", new Long(dbVersion_));
            log.info(temp.getAbsolutePath());

            result.add(new FileTransaction(header, temp.getAbsolutePath()));
        } catch (IOException e) {
            log.error(e);
        }

        return result;
    }

    private static LinkedList<SimpleTransaction> addPicturesToGroupTransaction(long userId,
                                                                               int clientVersion_,
                                                                               int dbVersion_) {
        LinkedList<SimpleTransaction> result = new LinkedList<SimpleTransaction>();
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
                    fileHeader.put("type", new Long(BluetoothPacketType.BINARY_FILE.getId()));
                    fileHeader.put("userId", new Long(userId));
                    try {
                        fileHeader.put("size", new Long(Files.size(path)));
                    } catch (IOException e) {
                        log.error(e);
                    }
                    fileHeader.put("filename", currentFilename);
                    result.add(new FileTransaction(fileHeader, path.toAbsolutePath().toString()));
                }
            }
        }
    return result;
}

    private static FileTransaction addReplaceDatabaseToGroupTransaction(long userId, int dbVersion_) throws FileNotFoundException {
        //передать файлик базы данных целиком
        File databaseFile = new File(ProjectDirectories.commonDatabaseRelativePath);
        if (databaseFile.exists()) {
            JSONObject header = new JSONObject();
            header.put("type", new Long(BluetoothPacketType.REPLACE_DATABASE.getId()));
            header.put("userId", new Long(userId));
            header.put("size", new Long(databaseFile.length()));
            header.put("version", new Long(dbVersion_));

            return new FileTransaction(header, databaseFile.getAbsolutePath());
        }
        throw new FileNotFoundException();
    }

    private static SimpleTransaction addEndTransactionToGroupTransaction(int versionDb_) {
        //Ставим в группу для отправки закрытия сессии после получения последнего RESPONSE
        JSONObject sessionCloseHeader = new JSONObject();
        sessionCloseHeader.put("type", new Long(BluetoothPacketType.END_TRANSACTION.getId()));
        sessionCloseHeader.put("version", new Long(versionDb_));
        return new SimpleTransaction(sessionCloseHeader);
    }

    private static SimpleTransaction addSessionCloseToGroupTransaction(int versionDb_) {
        //Ставим в группу для отправки закрытия сессии после получения последнего RESPONSE
        JSONObject sessionCloseHeader = new JSONObject();
        sessionCloseHeader.put("type", new Long(BluetoothPacketType.SESSION_CLOSE.getId()));
        sessionCloseHeader.put("version", new Long(versionDb_));
        return new SimpleTransaction(sessionCloseHeader);
    }

    private static LinkedList<SimpleTransaction> addPicturesFromTableToGroupTransaction(int userId) {
        LinkedList<SimpleTransaction> result = new LinkedList<SimpleTransaction>();
        ArrayList<String> pictures = new ArrayList<String>();

        pictures.addAll(databaseDriver.getPictures());

        //Ставим в очередь передачу файликов
        if (!pictures.isEmpty()) {
            for (Object currentFilename : pictures) {
                Path path = Paths.get(ProjectDirectories.directoryDownloads + "/" + currentFilename);
                if (Files.exists(path)) {
                    JSONObject fileHeader = new JSONObject();
                    fileHeader.put("type", new Long(BluetoothPacketType.BINARY_FILE.getId()));
                    fileHeader.put("userId", new Long(userId));
                    try {
                        fileHeader.put("size", new Long(Files.size(path)));
                    } catch (IOException e) {
                        log.error(e);
                    }
                    fileHeader.put("filename", currentFilename);
                    result.add(new FileTransaction(fileHeader, path.toAbsolutePath().toString()));
                }
            }
        }
        return result;
    }

    public static JSONArray getUsersList() {
        ArrayList<UserData> users = databaseDriver.getUsersList();
        JSONArray result = new JSONArray();

        for( UserData i : users) {
            JSONObject user = new JSONObject();

            user.put("id", i._id_user);
            user.put("fio", i.fio);
            user.put("position", i.id_position);

            result.add(user);
        }

        return result;
    }

    public static JSONArray getRoutesList() {
        ArrayList<RouteData> routes = databaseDriver.getRoutesList();
        JSONArray result = new JSONArray();

        for( RouteData i : routes) {
            JSONObject route = new JSONObject();

            route.put("id", i._id_route);
            route.put("name", i.name);

            result.add(route);
        }

        return result;
    }

    public static JSONArray getFilteredDetour(int userId, int routeId, int rowNumber, String startDate, String finishDate) {
        ArrayList<DetourData> detour = databaseDriver.getFilteredDetour(userId, routeId, rowNumber, startDate, finishDate);
        JSONArray result = new JSONArray();

        for( DetourData i : detour) {
            JSONObject detourRow = new JSONObject();

            detourRow.put("_id_detour", i._id_detour);

            String user_name = databaseDriver.getUserName(i.id_user);
            detourRow.put("user_name", user_name);

            String route_name = databaseDriver.getRouteName(i.id_route);
            detourRow.put("route_name", route_name);

            detourRow.put("start_time", i.time_start);
            detourRow.put("end_time", i.time_stop);
            result.add(detourRow);
        }

        return result;
    }
}
