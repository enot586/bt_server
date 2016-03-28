package reportserver;

import org.apache.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

public class ReportDatabaseDriver {
    private String url;
    private Statement databaseStatement;
    private Connection dbConnection;
    private Integer dataBaseSynchId = 0;
    private static final Logger log = Logger.getLogger(ReportDatabaseDriver.class);

    public enum DatabaseState {
        CLOSE,
        OPEN,
        BACKUP
    }

    private DatabaseState dbState = DatabaseState.CLOSE;

    void init(String url_) throws SQLException {
        url = url_;

        try {
            Class.forName("org.sqlite.JDBC");

            //DriverManager.registerDriver( new org.sqlite.JDBC() );
            //dbConnection = DriverManager.getConnection("jdbc:sqlite:"+url);

            DriverManager.registerDriver( new net.sf.log4jdbc.DriverSpy() );
            dbConnection = DriverManager.getConnection("jdbc:log4jdbc:sqlite:"+url);

            if (dbConnection == null)
                throw new SQLException();

            databaseStatement = dbConnection.createStatement();

            synchronized (dbState) {
                dbState = DatabaseState.OPEN;
            }

        } catch (ClassNotFoundException e) {
            log.error(e);
        } catch (SQLException e) {
            log.error(e);
        }
    }

    ArrayList<String> getClientHistory(int version) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT query FROM history WHERE id_version="+version);
        ArrayList<String> resultList = new ArrayList<String>();

        while (rs.next()) {
            resultList.add(rs.getString("id_version"));
        }

        return resultList;
    }

    public int checkClientVersion(String mac_address) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT id_version FROM clients_version WHERE mac = \""+mac_address+"\"");

        if (!rs.next()) {
            databaseStatement.executeUpdate("INSERT INTO clients_version (id_version, mac) VALUES (0, \""+mac_address+"\")");
            return 0;
        } else {
            return (int)rs.getInt("id_version");
        }
    }

    public void setClientVersion(String mac_address, int id_version) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT id_version FROM clients_version WHERE mac=\""+mac_address+"\"");

        if (!rs.next()) {
            databaseStatement.executeUpdate("INSERT INTO clients_version (id_version, mac) VALUES ("+id_version+", \""+mac_address+"\")");
        } else {
            databaseStatement.executeUpdate("UPDATE clients_version id_version="+id_version+" WHERE mac=\""+mac_address+"\"");
        }

    }

    public int getDatabaseVersion() {
        return dataBaseSynchId;
    }

    public Integer getDatabaseVersion(String mac_address) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT id_version FROM clients_version WHERE mac=\""+mac_address+"\"");
        if (!rs.next()) {
            return null;
        }

        return new Integer(rs.getInt("id_version"));
    }

    synchronized public DatabaseState getBdState() {
        return dbState;
    }

    void backupCurrentDatabase(String uniqPart) {
        synchronized (dbState) {
            dbState = DatabaseState.BACKUP;
        }

        try {
            databaseStatement.close();
            dbConnection.close();

            String synchDataBaseFile = "base-synchronization";

            FileHandler fileHandler = new FileHandler(synchDataBaseFile);

            File sourceFile = new File(synchDataBaseFile+"/"+"app-data.db3");
            File targetFile = new File(synchDataBaseFile+"/"+fileHandler.generateName("app-data-"+uniqPart, "bak"));

            Files.copy(Paths.get(sourceFile.getAbsolutePath()),
                        new FileOutputStream(targetFile));

            dbConnection = DriverManager.getConnection("jdbc:log4jdbc:sqlite:"+url);

            if (dbConnection == null)
                throw new SQLException();

            databaseStatement = dbConnection.createStatement();

            synchronized (dbState) {
                dbState = DatabaseState.OPEN;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            synchronized (dbState) {
                dbState = DatabaseState.CLOSE;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            synchronized (dbState) {
                dbState = DatabaseState.CLOSE;
            }
        } catch (IOException e) {
            e.printStackTrace();
            synchronized (dbState) {
                dbState = DatabaseState.CLOSE;
            }
        }
    }

    public void setToHistory(String query, int databaseId) throws SQLException {
        //Записать все запросы в историю для текущей версии таблицы
        databaseStatement.executeUpdate("INSERT INTO history (id_version, query) VALUES("+databaseId+",\""+query+"\")");
    }

    public void initDatabaseVersion(String mac) throws SQLException {
        dataBaseSynchId = getDatabaseVersion(mac);

        if (null == dataBaseSynchId) {
            databaseStatement.executeUpdate("INSERT INTO clients_version (id_version, mac) VALUES("+0+",\""+mac+"\")");
            dataBaseSynchId = new Integer(0);
        }
    }

    private void incrementDatabaseVersion(String mac_address) throws SQLException {
        databaseStatement.executeUpdate("UPDATE clients_version SET id_version="+(dataBaseSynchId+1)+" WHERE mac=\""+mac_address+"\"");
        ++dataBaseSynchId;
    }

    void runScript(int userId, SqlCommandList batch) {
        try {
            log.info("current user: "+userId);
            boolean isAdmin = isUserAdmin(userId);

            dbConnection.setAutoCommit(false);

            ListIterator<String> iter = (ListIterator<String>) batch.iterator();
            while (iter.hasNext()) {
                String query = iter.next();
                if (query.length() == 0) continue;
                if (isAdmin || isUserAcceptableTableInQuery(query))  {
                    databaseStatement.executeUpdate(query);
                    setToHistory(query, dataBaseSynchId+1);
                }
            }

            //если все этапы прошли корректно увеличиваем версию
            if (isAdmin) {
                incrementDatabaseVersion(ReportServer.getBluetoothMacAddress());
            }

            dbConnection.commit();

        } catch (SQLException e) {
            ReportServer.sendUserMessage("Ошибка обработки SQL-запроса. Отмена синхронизации.");
            try {
                dbConnection.rollback();
            } catch (SQLException e1) {
                log.warn(e1);
            }
        }
        finally {
            try {
                dbConnection.setAutoCommit(true);
            } catch (SQLException e) {
                log.warn(e);
            }
        }
    }

    public boolean isUserAdmin(int userId) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT is_admin FROM users WHERE _id_user="+userId);
        return rs.getBoolean("is_admin");
    }

    public boolean isUserAcceptableTableInQuery(String query) {
        String[] words = query.split(" ");
        if ((words[0].equals("INSERT")) || (words[0].equals("DELETE") )) {
            return (words[2].equals("detour"));
        }

        if (words[0].equals("UPDATE")) {
            return (words[1].equals("detour"));
        }

        return false;
    }

    public ArrayList<Integer> getLatest10IdsDetour() throws SQLException {
        ArrayList<Integer> ids = new  ArrayList<Integer>();

        ResultSet rs = databaseStatement.executeQuery("SELECT _id_detour FROM detour ORDER BY _id_detour DESC LIMIT 10");

        int i = 0;
        while (rs.next()) {
            int id = rs.getInt("_id_detour");
            ids.add(i++, id);
        }
        return ids;

    }

    public ArrayList<Integer> getDetourTableIds() throws SQLException {
        ArrayList<Integer> ids = new  ArrayList<Integer>();

        ResultSet rs = databaseStatement.executeQuery("SELECT _id_detour FROM detour");

        int i = 0;
        while (rs.next()) {
            int id = rs.getInt("_id_detour");
            ids.add(i++, id);
        }
        return ids;
    }

    public String getUserNameFromDetourTable(int idDetour) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT id_user FROM detour WHERE _id_detour="+idDetour);
        int userId = rs.getInt("id_user");
        rs = databaseStatement.executeQuery("SELECT fio FROM users WHERE _id_user="+userId);
        return rs.getString("fio");
    }

    public String getRouteNameFromDetourTable(int idDetour) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT id_route FROM detour WHERE _id_detour="+idDetour);
        int routeId = rs.getInt("id_route");
        rs = databaseStatement.executeQuery("SELECT name FROM routs WHERE _id_route="+routeId);
        return rs.getString("name");
    }

    public String getStartTimeFromDetourTable(int idDetour) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT time_start FROM detour WHERE _id_detour="+idDetour);
        return rs.getString("time_start");
    }

    public String getEndTimeFromDetourTable(int idDetour) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT time_stop FROM detour WHERE _id_detour="+idDetour);
        return rs.getString("time_stop");
    }

    public boolean getStatusFromDetourTable(int idDetour) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT finished FROM detour WHERE _id_detour="+idDetour);
        return rs.getBoolean("finished");
    }

    public String getRoutesTablePathRoutePicture(int idRoute) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT path_picture_route FROM routs WHERE _id_route="+idRoute);
        return rs.getString("path_picture_route");
    }

    public int getUserMessageNumber() {
        try {
            ResultSet rs1 = databaseStatement.executeQuery("SELECT COUNT(message_date) FROM user_messages");
            if (rs1.next()) {
                int rowNumber = rs1.getInt(1);
                return rowNumber;
            }
        } catch (SQLException e) {
            log.warn(e);
            return 0;
        }
        return 0;
    }

    public String[] getUserMessages() {
        try {
            ResultSet rs = databaseStatement.executeQuery("SELECT message FROM user_messages ORDER BY message_date DESC");
            String[] messages = new String[15];

            for (int i = 0; i < 15; ++i) {
                if (rs.next()) messages[i] = rs.getString("message");
            }

            return messages;
        } catch (SQLException e) {
            log.warn(e);
        }
        return null;
    }

    public String[] getUserMessagesDate() {
        try {
            ResultSet rs = databaseStatement.executeQuery("SELECT message_date FROM user_messages ORDER BY message_date DESC");
            String[] dates = new String[15];

            for (int i = 0; i < 15; ++i) {
                if (rs.next()) dates[i] = rs.getString("message_date");
            }

            return dates;
        } catch (SQLException e) {
            log.warn(e);
        }
        return null;
    }

    public void addUserMessageToDatabase(Date currentDate, String text) {
        try {
            int rowNumber = getUserMessageNumber();

            if (rowNumber >= 15) {
                ResultSet rs = databaseStatement.executeQuery("SELECT _id_message, MIN(message_date) FROM user_messages");

                if (rs.next()) {
                    int currentId = (int) rs.getInt("_id_message");
                    databaseStatement.executeUpdate("DELETE FROM user_messages WHERE _id_message=" + currentId);
                }
            }

            String pattern = "yyyy-mm-dd HH:mm:ss.SSS";
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
            String date = simpleDateFormat.format(currentDate);
            databaseStatement.executeUpdate("INSERT INTO user_messages (message, message_date) VALUES ('"+text+"','"+date+"')");
        } catch (SQLException e) {
            log.error(e);
        }
    }
}
