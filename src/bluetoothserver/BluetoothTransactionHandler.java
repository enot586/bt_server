package reportserver;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import javax.servlet.AsyncContext;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

public class BluetoothTransactionHandler implements Runnable {
    private BluetoothServer bluetoothServer;
    private DatabaseDriver databaseDriver;
    private CommonUserInterface userFeedback;
    private GroupTransactionHandler bluetoothGroupTransactionHandler;
    private GroupTransaction groupTransaction;
    private int currentConnectionId = 0;
    private SqlCommandList sqlScript;

    private final Logger log = Logger.getLogger(BluetoothTransactionHandler.class);

    BluetoothTransactionHandler(BluetoothServer btServer_, DatabaseDriver dbDriver_,
                                CommonUserInterface messageForUser_) {
        userFeedback = messageForUser_;

        bluetoothServer = btServer_;
        bluetoothGroupTransactionHandler = new GroupTransactionHandler(bluetoothServer);

        databaseDriver = dbDriver_;
    }

    @Override
    public void run() {
        if (groupTransaction != null) {
            bluetoothGroupTransactionHandler.timerHandler(groupTransaction);
            if (groupTransaction.isComplete()) {
                groupTransaction = null;
            }
        }

        try {
            SimpleTransaction newReceivedTransaction = bluetoothServer.getFirstReceivedTransaction();

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
                    bluetoothSynchTransactionHandler(newReceivedTransaction);
                    break;
                }

                if (BluetoothPacketType.SQL_QUERIES.getId() == type) {
                    try {
                        bluetoothSqlQueriesTransactionHandler((FileTransaction) newReceivedTransaction);
                    } catch (ClassCastException e) {
                        log.warn(e);
                    }
                    break;
                }

                if (BluetoothPacketType.BINARY_FILE.getId() == type) {
                    try {
                        bluetoothBinaryFileTransactionHandler((FileTransaction) newReceivedTransaction);
                    } catch (ClassCastException e) {
                        log.warn(e);
                    }
                    break;
                }

                if (BluetoothPacketType.REPLACE_DATABASE.getId() == type) {
                    try {
                        bluetoothReplaceDatabaseTransactionHandler((FileTransaction) newReceivedTransaction);
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
                    bluetoothServer.sendData(new SimpleTransaction(header));
                    userFeedback.sendUserMessage("Окончание текущей транзакции");
                    break;
                }

                if (BluetoothPacketType.SESSION_CLOSE.getId() == type) {
                    bluetoothServer.reopenNewConnection();
                    userFeedback.sendUserMessage("Текущее соедение завершено. Ожидаю нового подключения.");
                    break;
                }
            } while (false);

            //Если обработка произошла без исключений удаляем первую транзакцию из списка
            bluetoothServer.removeFirstReceivedTransaction();
        } catch (NoSuchElementException e) {
        }
    }

    protected void bluetoothReplaceDatabaseTransactionHandler(FileTransaction transaction) {
        int status = BluetoothTransactionStatus.DONE.getId();

        JSONObject header = new JSONObject();
        header.put("type", new Long(BluetoothPacketType.RESPONSE.getId()) );
        header.put("userId", (Long)transaction.getHeader().get("userId"));
        header.put("size", (Long)transaction.getHeader().get("size"));
        header.put("status", new Long(status));
        bluetoothServer.sendData(new SimpleTransaction(header));

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
            databaseDriver.initDatabaseVersion(bluetoothServer.getLocalHostMacAddress());
            databaseDriver.setClientVersion(bluetoothServer.getRemoteDeviceBluetoothAddress(), 1);

            //чтобы исключить инкремент версии при получении файлов, если они будут отосланы
            currentConnectionId = bluetoothServer.getConnectionId();
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

    protected void bluetoothBinaryFileTransactionHandler(FileTransaction transaction) {

        File scriptFile = new File(ProjectDirectories.directoryDownloads + "/" + transaction.getFileName());

        int status = BluetoothTransactionStatus.DONE.getId();

        JSONObject header = new JSONObject();
        header.put("type", new Long(BluetoothPacketType.RESPONSE.getId()) );
        header.put("userId", (Long)transaction.getHeader().get("userId"));
        header.put("size", (Long)transaction.getHeader().get("size"));
        header.put("status", new Long(status));
        bluetoothServer.sendData(new SimpleTransaction(header));

        userFeedback.sendUserMessage("Принят файл: "+transaction.getFileName());

        //сверяем connectionId и если не совпадает увеличиваем версию,
        //если connectionId одинаковый, считаем все изменения проходят в рамках текущей версии
        boolean isNeedToIncrementDbVersion = (currentConnectionId != bluetoothServer.getConnectionId());

        try {
            databaseDriver.addFileToHistory(bluetoothServer.getLocalHostMacAddress(),
                    scriptFile.toPath(), isNeedToIncrementDbVersion);
            databaseDriver.setClientVersion(bluetoothServer.getRemoteDeviceBluetoothAddress(), databaseDriver.getDatabaseVersion());
        } catch (IOException|SQLException e) {
            log.error(e);
            userFeedback.sendUserMessage("Ошибка при обращении к базе даных.");
        }

        currentConnectionId = bluetoothServer.getConnectionId();
    }

    protected void bluetoothSynchTransactionHandler(SimpleTransaction transaction) {
        if (!transaction.getHeader().containsKey("userId") ||
                !transaction.getHeader().containsKey("version")) {
            userFeedback.sendUserMessage("Ошибка: Некорректный формат SYNCH_REQUEST.");
            return;
        }

        int userId = ((Long) transaction.getHeader().get("userId")).intValue();
        int realClientVersion = ((Long) transaction.getHeader().get("version")).intValue();

        int clientVersion = 0;
        try {
            clientVersion = databaseDriver.checkClientVersion(bluetoothServer.getRemoteDeviceBluetoothAddress());
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
                                    databaseDriver.setClientVersion(bluetoothServer.getRemoteDeviceBluetoothAddress(), dbVersion);
                                } catch (IOException | SQLException e) {
                                    log.error(e);
                                    userFeedback.sendUserMessage("Ошибка: не удалось инкрементировать весию БД для клиента.");
                                }
                            }

                            @Override
                            public void fail() {
                                bluetoothServer.reopenNewConnection();
                                log.warn("client RESPONSE fail");
                                userFeedback.sendUserMessage("Ошибка: не удалось получить ответ от клиента");
                            }
                        }
                );

                if (bluetoothServer.sendData(groupTransaction)) {
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
                                databaseDriver.setClientVersion(bluetoothServer.getRemoteDeviceBluetoothAddress(),
                                        databaseDriver.getDatabaseVersion());
                            } catch (IOException | SQLException e) {
                                log.error(e);
                                userFeedback.sendUserMessage("Ошибка: не удалось инкрементировать весию БД.");
                            }
                        }

                        @Override
                        public void fail() {
                            bluetoothServer.reopenNewConnection();
                            log.warn("client RESPONSE fail");
                            userFeedback.sendUserMessage("Ошибка: не удалось получить ответ от клиента");
                        }
                    }
            );

            if (bluetoothServer.sendData(groupTransaction)) {
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
                if(bluetoothServer.sendData(new SimpleTransaction(header))) {
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
                                    databaseDriver.setClientVersion(bluetoothServer.getRemoteDeviceBluetoothAddress(),
                                            databaseDriver.getDatabaseVersion());
                                } catch (IOException | SQLException e) {
                                    log.error(e);
                                    userFeedback.sendUserMessage("Ошибка: не удалось инкрементировать весию БД.");
                                }
                            }

                            @Override
                            public void fail() {
                                bluetoothServer.reopenNewConnection();
                                log.warn("client RESPONSE fail");
                                userFeedback.sendUserMessage("Ошибка: не удалось получить ответ от клиента");
                            }
                        }
                );
                if (bluetoothServer.sendData(groupTransaction)) {
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

    protected void bluetoothSqlQueriesTransactionHandler(FileTransaction transaction) {
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
        bluetoothServer.sendData(new SimpleTransaction(header));

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
            boolean isNeedToIncrementDbVersion = (currentConnectionId != bluetoothServer.getConnectionId());
            databaseDriver.runScript(isAdmin, bluetoothServer.getLocalHostMacAddress(),
                                    bluetoothServer.getRemoteDeviceBluetoothAddress(),
                                    sqlScript, isNeedToIncrementDbVersion);

            currentConnectionId = bluetoothServer.getConnectionId();
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

    protected LinkedList<SimpleTransaction> addSqlHistoryToGroupTransaction(long userId,
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

    protected LinkedList<SimpleTransaction> addPicturesToGroupTransaction(long userId,
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

    protected FileTransaction addReplaceDatabaseToGroupTransaction(long userId, int dbVersion_) throws FileNotFoundException {
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

    protected SimpleTransaction addEndTransactionToGroupTransaction(int versionDb_) {
        //Ставим в группу для отправки закрытия сессии после получения последнего RESPONSE
        JSONObject sessionCloseHeader = new JSONObject();
        sessionCloseHeader.put("type", new Long(BluetoothPacketType.END_TRANSACTION.getId()));
        sessionCloseHeader.put("version", new Long(versionDb_));
        return new SimpleTransaction(sessionCloseHeader);
    }

    protected SimpleTransaction addSessionCloseToGroupTransaction(int versionDb_) {
        //Ставим в группу для отправки закрытия сессии после получения последнего RESPONSE
        JSONObject sessionCloseHeader = new JSONObject();
        sessionCloseHeader.put("type", new Long(BluetoothPacketType.SESSION_CLOSE.getId()));
        sessionCloseHeader.put("version", new Long(versionDb_));
        return new SimpleTransaction(sessionCloseHeader);
    }

    protected LinkedList<SimpleTransaction> addPicturesFromTableToGroupTransaction(int userId) {
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

}
