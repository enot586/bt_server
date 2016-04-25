package reportserver;

import org.apache.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class DatabaseDriver {
    private String commonUrl;
    private Statement commonDatabaseStatement;
    private Connection commonDatabaseConnection;

    private String localUrl;
    private Statement localDatabaseStatement;
    private Connection localDatabaseConnection;

    private Integer dataBaseSynchId = 0;

    private ReentrantLock dbGuard = new ReentrantLock();

    private static final Logger log = Logger.getLogger(DatabaseDriver.class);

    private enum DatabaseState {
        CLOSE,
        OPEN,
        BACKUP
    }

    private DatabaseState dbState = DatabaseState.CLOSE;

    void init(String commonUrl_, String localUrl_) throws SQLException {
        commonUrl = commonUrl_;
        localUrl = localUrl_;

        try {
            Class.forName("org.sqlite.JDBC");

            //DriverManager.registerDriver( new org.sqlite.JDBC() );
            DriverManager.registerDriver( new net.sf.log4jdbc.DriverSpy() );

            //dbConnection = DriverManager.getConnection("jdbc:sqlite:"+commonUrl);
            commonDatabaseConnection = DriverManager.getConnection("jdbc:log4jdbc:sqlite:"+commonUrl);

            if (commonDatabaseConnection == null)
                throw new SQLException();

            commonDatabaseStatement = commonDatabaseConnection.createStatement();

            //dbConnection = DriverManager.getConnection("jdbc:sqlite:"+localUrl);
            localDatabaseConnection = DriverManager.getConnection("jdbc:log4jdbc:sqlite:"+localUrl);

            if (localDatabaseConnection == null)
                throw new SQLException();

            localDatabaseStatement = localDatabaseConnection.createStatement();

            synchronized (dbState) {
                dbState = DatabaseState.OPEN;
            }
        } catch (SQLException|ClassNotFoundException e) {
            log.error(e);
        }
    }

    public DatabaseState getBdState() {
        return dbState;
    }

    void backupCurrentDatabase(String uniqPart) {
        dbState = DatabaseState.BACKUP;

        try {
            dbGuard.lock();
            commonDatabaseStatement.close();
            commonDatabaseConnection.close();

            FileHandler fileHandler = new FileHandler(ProjectDirectories.directoryDatabase);

            File sourceFile = new File(commonUrl);
            File targetFile = new File(ProjectDirectories.directoryDatabase+"/"+fileHandler.generateName("app-data-"+uniqPart, "bak"));

            Files.copy(Paths.get(sourceFile.getAbsolutePath()),
                        new FileOutputStream(targetFile));
            do {
                try {
                    commonDatabaseConnection = DriverManager.getConnection("jdbc:log4jdbc:sqlite:" + commonUrl);

                    if (commonDatabaseConnection == null)
                        continue;

                    commonDatabaseStatement = commonDatabaseConnection.createStatement();
                } catch (SQLException e) {
                    commonDatabaseConnection.close();
                }
            } while (!commonDatabaseConnection.isValid(2));

            synchronized (dbState) {
                dbState = DatabaseState.OPEN;
            }
        } catch (IOException | SQLException e) {
            log.error(e);
            synchronized (dbState) {
                dbState = DatabaseState.CLOSE;
            }
        } finally {
            dbGuard.unlock();
        }
    }

    void runScript(boolean isAdmin, String clientAddress, SqlCommandList batch, boolean isNeedToIncrementVersion) throws SQLException {
        try {
            dbGuard.lock();
            commonDatabaseConnection.setAutoCommit(false);
            localDatabaseConnection.setAutoCommit(false);

            ListIterator<String> iter = (ListIterator<String>) batch.iterator();
            while (iter.hasNext()) {
                String query = iter.next();
                if (query.length() == 0) continue;
                if (isAdmin || isUserAcceptableTableInQuery(query))  {
                    commonDatabaseStatement.executeUpdate(query);
                    if (isNeedToIncrementVersion) {
                        setToHistory(query, getDatabaseVersion() + 1);
                    }
                    else
                    {
                        setToHistory(query, getDatabaseVersion());
                    }
                }
            }

            //если все этапы прошли корректно увеличиваем версию в базе
            if (isNeedToIncrementVersion) {
                incrementDatabaseVersion(ReportServer.getBluetoothMacAddress());
            }

            setClientVersion(clientAddress, getDatabaseVersion());

            commonDatabaseConnection.commit();
            localDatabaseConnection.commit();

        } catch (SQLException e) {
            log.warn(e);
            try {
                commonDatabaseConnection.rollback();
                localDatabaseConnection.rollback();
            } catch (SQLException e1) {
                log.warn(e1);
            }

            throw e;
        }
        finally {
            try {
                commonDatabaseConnection.setAutoCommit(true);
                localDatabaseConnection.setAutoCommit(true);
            } catch (SQLException e2) {
                log.error(e2);
            }
            dbGuard.unlock();
        }
    }

    public void replaceCommonBase(File newBase) throws IOException {
        try {
            dbGuard.lock();
            commonDatabaseConnection.close();

            try {
                File currentBase = new File(commonUrl);

                FileHandler fh = new FileHandler(ProjectDirectories.directoryDatabase);
                currentBase.renameTo(new File(ProjectDirectories.directoryDatabase+"/"+fh.generateName("app-data","bak")));

                Files.copy( newBase.toPath(), currentBase.toPath(),
                            StandardCopyOption.REPLACE_EXISTING );
            } catch (IOException e) {
                log.warn(e);
                throw e;
            }

            do {
                try {
                    commonDatabaseConnection = DriverManager.getConnection("jdbc:log4jdbc:sqlite:" + commonUrl);

                    if (commonDatabaseConnection == null)
                        continue;

                    commonDatabaseStatement = commonDatabaseConnection.createStatement();
                } catch (SQLException e) {
                    commonDatabaseConnection.close();
                }
            } while (!commonDatabaseConnection.isValid(2));

        } catch (SQLException e) {
           log.warn(e);
        } finally {
            dbGuard.unlock();
        }
    }

    public boolean isUserAdmin(int userId) throws SQLException {
        try {
            dbGuard.lock();
            ResultSet rs = commonDatabaseStatement.executeQuery("SELECT is_admin FROM users WHERE _id_user=" + userId);
            return rs.getBoolean("is_admin");
        } finally {
            dbGuard.unlock();
        }
    }

    public boolean isUserAcceptableTableInQuery(String query) {
        String[] words = query.split(" ");
        if (words[0].equals("INSERT") || words[0].equals("DELETE")) {
            return (words[2].equals("detour")) || (words[2].equals("visits"));
        }

        return words[0].equals("UPDATE") && (words[2].equals("detour") || words[2].equals("visits"));
    }

    public int checkClientVersion(String mac_address) throws SQLException {
        try {
            dbGuard.lock();
            ResultSet rs = localDatabaseStatement.executeQuery("SELECT id_version FROM clients_version WHERE mac='" + mac_address + "'");

            if (!rs.next()) {
                localDatabaseStatement.executeUpdate("INSERT INTO clients_version (id_version, mac) VALUES (0, '" + mac_address + "')");
                return 0;
            } else {
                return (int) rs.getInt("id_version");
            }
        } finally {
            dbGuard.lock();
        }
    }

    public void setClientVersion(String mac_address, int id_version) throws SQLException {
        try {
            dbGuard.lock();
            ResultSet rs = localDatabaseStatement.executeQuery("SELECT id_version FROM clients_version WHERE mac='" + mac_address + "'");

            if (!rs.next()) {
                localDatabaseStatement.executeUpdate("INSERT INTO clients_version (id_version, mac) VALUES (" + id_version + ", '" + mac_address + "')");
            } else {
                localDatabaseStatement.executeUpdate("UPDATE clients_version SET id_version=" + id_version + " WHERE mac='" + mac_address + "'");
            }
        } finally {
            dbGuard.unlock();
        }
    }

    ArrayList<String> getClientHistory(int version) throws SQLException {
        try {
            dbGuard.lock();
            ResultSet rs = localDatabaseStatement.executeQuery("SELECT query FROM history WHERE id_version=" + version);
            ArrayList<String> resultList = new ArrayList<String>();

            while (rs.next()) {
                String prepare = rs.getString("query");
                resultList.add(prepare.replace("&", "'"));
            }

            return resultList;
        } finally {
            dbGuard.unlock();
        }
    }

    ArrayList<String> getClientPicturesHistory(int version) throws SQLException {
        try {
            dbGuard.lock();
            ResultSet rs = localDatabaseStatement.executeQuery("SELECT filename FROM pictures_history WHERE id_version=" + version);
            ArrayList<String> resultList = new ArrayList<String>();

            while (rs.next()) {
                resultList.add(rs.getString("filename"));
            }

            return resultList;
        } finally {
            dbGuard.unlock();
        }
    }

    public int getDatabaseVersion() {
        return dataBaseSynchId;
    }

    public void setToHistory(String query, int databaseId) throws SQLException {
        try {
            dbGuard.lock();
            String prepare = query.replace("'", "&");
            //Записать все запросы в историю для текущей версии таблицы
            localDatabaseStatement.executeUpdate("INSERT INTO history (id_version, query) VALUES(" + databaseId + ",'" + prepare + "')");
        } finally {
            dbGuard.unlock();
        }
    }

    public void setFileToHistory(Path file, int databaseId) throws SQLException {
        try {
            dbGuard.lock();
            //Записать все запросы в историю для текущей версии таблицы
            localDatabaseStatement.executeUpdate("INSERT INTO pictures_history (id_version, filename) VALUES(" + databaseId + ",'" + file.getFileName().toString() + "')");
        } finally {
            dbGuard.unlock();
        }
    }

    public Integer getDatabaseVersion(String mac_address) throws SQLException {
        try {
            dbGuard.lock();
            ResultSet rs = localDatabaseStatement.executeQuery("SELECT id_version FROM clients_version WHERE mac='" + mac_address + "'");
            if (!rs.next()) {
                return null;
            }

            return rs.getInt("id_version");
        } finally {
            dbGuard.unlock();
        }
    }

    public void incrementDatabaseVersion(String mac_address) throws SQLException {
        try {
            dbGuard.lock();
            localDatabaseStatement.executeUpdate("UPDATE clients_version SET id_version=" + (dataBaseSynchId + 1) + " WHERE mac=\"" + mac_address + "\"");
            ++dataBaseSynchId;
        } finally {
            dbGuard.unlock();
        }
    }

    public void initDatabaseVersion(String mac) throws SQLException {
        dataBaseSynchId = getDatabaseVersion(mac);

        try {
            dbGuard.lock();
            if (null == dataBaseSynchId) {
                localDatabaseStatement.executeUpdate("INSERT INTO clients_version (id_version, mac) VALUES(" + 1 + ",'" + mac + "')");
                dataBaseSynchId = 1;
            }
        } finally {
            dbGuard.unlock();
        }
    }

    public int getUserMessageNumber() {
        try {
            dbGuard.lock();
            ResultSet rs1 = localDatabaseStatement.executeQuery("SELECT COUNT(message_date) AS count FROM user_messages");
            if (rs1.next()) {
                return rs1.getInt("count");
            }
        } catch (SQLException e) {
            log.warn(e);
            return 0;
        } finally {
            dbGuard.unlock();
        }
        return 0;
    }

    public String[] getUserMessages() {
        try {
            dbGuard.lock();
            ResultSet rs = localDatabaseStatement.executeQuery("SELECT message FROM user_messages ORDER BY message_date ASC");
            String[] messages = new String[30];

            for (int i = 0; i < 30; ++i) {
                if (rs.next()) messages[i] = rs.getString("message");
            }

            return messages;
        } catch (SQLException e) {
            log.warn(e);
        } finally {
            dbGuard.unlock();
        }
        return null;
    }

    public String[] getUserMessagesDate() {
        try {
            dbGuard.lock();
            ResultSet rs = localDatabaseStatement.executeQuery("SELECT message_date FROM user_messages ORDER BY message_date ASC");
            String[] dates = new String[30];

            for (int i = 0; i < 30; ++i) {
                if (rs.next()) dates[i] = rs.getString("message_date");
            }

            return dates;
        } catch (SQLException e) {
            log.warn(e);
        } finally {
            dbGuard.unlock();
        }
        return null;
    }

    public void addUserMessageToDatabase(java.util.Date currentDate, String text) {
        try {
            int rowNumber = getUserMessageNumber();

            dbGuard.lock();
            if (rowNumber >= 30) {
                ResultSet rs = localDatabaseStatement.executeQuery("SELECT _id_message, MIN(message_date) FROM user_messages");

                if (rs.next()) {
                    int currentId = (int) rs.getInt("_id_message");
                    localDatabaseStatement.executeUpdate("DELETE FROM user_messages WHERE _id_message=" + currentId);
                }
            }

            String pattern = "yyyy-MM-dd HH:mm:ss.SSS";
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
            String date = simpleDateFormat.format(currentDate);
            localDatabaseStatement.executeUpdate("INSERT INTO user_messages (message, message_date) VALUES ('"+text+"','"+date+"')");
        } catch (SQLException e) {
            log.error(e);
        } finally {
            dbGuard.unlock();
        }
    }

    public ArrayList<Integer> getLatest10IdsDetour() throws SQLException {
        ArrayList<Integer> ids = new  ArrayList<Integer>();
        try {
            dbGuard.lock();
            ResultSet rs = commonDatabaseStatement.executeQuery("SELECT _id_detour FROM detour ORDER BY _id_detour DESC LIMIT 10");

            int i = 0;
            while (rs.next()) {
                int id = rs.getInt("_id_detour");
                ids.add(i++, id);
            }
            return ids;
        } finally {
            dbGuard.unlock();
        }

    }

    public ArrayList<Integer> getDetourTableIds() throws SQLException {
        ArrayList<Integer> ids = new  ArrayList<Integer>();
        try {
            dbGuard.lock();
            ResultSet rs = commonDatabaseStatement.executeQuery("SELECT _id_detour FROM detour");

            int i = 0;
            while (rs.next()) {
                int id = rs.getInt("_id_detour");
                ids.add(i++, id);
            }
            return ids;
        } finally {
            dbGuard.unlock();
        }
    }

    public String getUserNameFromDetourTable(int idDetour) throws SQLException {
        try {
            dbGuard.lock();
            ResultSet rs = commonDatabaseStatement.executeQuery("SELECT id_user FROM detour WHERE _id_detour=" + idDetour);
            int userId = rs.getInt("id_user");
            rs = commonDatabaseStatement.executeQuery("SELECT fio FROM users WHERE _id_user=" + userId);
            return rs.getString("fio");
        } finally {
            dbGuard.unlock();
        }
    }

    public String getRouteNameFromDetourTable(int idDetour) throws SQLException {
        try {
            dbGuard.lock();
            ResultSet rs = commonDatabaseStatement.executeQuery("SELECT id_route FROM detour WHERE _id_detour=" + idDetour);
            int routeId = rs.getInt("id_route");
            rs = commonDatabaseStatement.executeQuery("SELECT name FROM routs WHERE _id_route=" + routeId);
            return rs.getString("name");
        } finally {
            dbGuard.unlock();
        }
    }

    public String getStartTimeFromDetourTable(int idDetour) throws SQLException {
        try {
            dbGuard.lock();
            ResultSet rs = commonDatabaseStatement.executeQuery("SELECT time_start FROM detour WHERE _id_detour=" + idDetour);
            return rs.getString("time_start");
        } finally {
            dbGuard.unlock();
        }
    }

    public String getEndTimeFromDetourTable(int idDetour) throws SQLException {
        try {
            dbGuard.lock();
            ResultSet rs = commonDatabaseStatement.executeQuery("SELECT time_stop FROM detour WHERE _id_detour=" + idDetour);
            return rs.getString("time_stop");
        } finally {
            dbGuard.unlock();
        }
    }

    public boolean getStatusFromDetourTable(int idDetour) throws SQLException {
        try {
            dbGuard.lock();
            ResultSet rs = commonDatabaseStatement.executeQuery("SELECT finished FROM detour WHERE _id_detour=" + idDetour);
            return rs.getBoolean("finished");
        } finally {
            dbGuard.unlock();
        }
    }

    public String getRoutesTablePathRoutePicture(int idRoute) throws SQLException {
        try {
            dbGuard.lock();
            ResultSet rs = commonDatabaseStatement.executeQuery("SELECT path_picture_route FROM routs WHERE _id_route=" + idRoute);
            return rs.getString("path_picture_route");
        } finally {
            dbGuard.unlock();
        }
    }

    synchronized public void addFileToHistory(Path file, boolean isNeedToIncrementVersion) throws SQLException {
        try {
            commonDatabaseConnection.setAutoCommit(false);
            localDatabaseConnection.setAutoCommit(false);

            //если все этапы прошли корректно увеличиваем версию
            if (isNeedToIncrementVersion) {
                setFileToHistory(file, getDatabaseVersion()+1);
                incrementDatabaseVersion(ReportServer.getBluetoothMacAddress());
            }
            else
            {
                setFileToHistory(file, getDatabaseVersion());
            }

            commonDatabaseConnection.commit();
            localDatabaseConnection.commit();
        } catch (SQLException e) {
            log.warn(e);
            try {
                commonDatabaseConnection.rollback();
                localDatabaseConnection.rollback();
            } catch (SQLException e1) {
                log.warn(e1);
            }
            throw e;
        }
        finally {
            try {
                commonDatabaseConnection.setAutoCommit(true);
                localDatabaseConnection.setAutoCommit(true);
            } catch (SQLException e2) {
                log.error(e2);
            }
        }
    }

    public void removeLocalHistory() throws SQLException {
        try {
            dbGuard.lock();
            localDatabaseStatement.executeUpdate("DELETE FROM history");
            localDatabaseStatement.executeUpdate("DELETE FROM pictures_history");
            localDatabaseStatement.executeUpdate("DELETE FROM clients_version");
        } finally {
            dbGuard.unlock();
        }
    }

    public ArrayList<String> getPictures() {
        ArrayList<String> result = new ArrayList<String>();
        try {
            dbGuard.lock();
            ResultSet rs = commonDatabaseStatement.executeQuery("SELECT path_picture FROM pictures");

            while (rs.next()) {
                File ddd = new File(rs.getString("path_picture"));
                result.add(ddd.getName());
            }
        } catch (SQLException e) {
           log.error(e);
        } finally {
            dbGuard.unlock();
        }

        return result;
    }
}
