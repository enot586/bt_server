package reportserver;

import org.apache.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.NoSuchElementException;

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

    ArrayList<String> GetClientHistory(int version) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT query FROM history WHERE id_version = " + version);
        ArrayList<String> resultList = new ArrayList<String>();

        while (rs.next()) {
            resultList.add(rs.getString("id_version"));
        }

        return resultList;
    }

    public int checkClientVersion(String mac_address) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT id_version FROM clients_version WHERE mac = \"" + mac_address + "\"");

        if (!rs.next()) {
            databaseStatement.executeUpdate("INSERT INTO clients_version (id_version, mac) VALUES (0, \"" + mac_address + "\")");
            return 0;
        } else {
            return (int)rs.getInt("id_version");
        }
    }

    public int getDatabaseVersion() {
        return dataBaseSynchId;
    }

    public Integer getDatabaseVersion(String mac_address) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT id_version FROM clients_version WHERE mac = \""+mac_address+"\"");
        if (!rs.next()) {
            return null;
        }

        return new Integer(rs.getInt("id_version"));
    }

    synchronized public DatabaseState getBdState() {
        return dbState;
    }

    void BackupCurrentDatabase(String uniqPart) {
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

            dbConnection = DriverManager.getConnection("jdbc:sqlite:"+url);

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

    private void SetToHistory(String query, int databaseId) throws SQLException {
        //Записать все запросы в историю для текущей версии таблицы
        databaseStatement.executeUpdate("INSERT INTO history (id_version, query) value("+databaseId+",\""+query+"\")");
    }

    public void initDatabaseVersion(String mac) throws SQLException {
        dataBaseSynchId = getDatabaseVersion(mac);

        if (null == dataBaseSynchId) {
            databaseStatement.executeUpdate("INSERT INTO clients_version (id_version, mac) VALUES("+0+",\""+mac+"\")");
            dataBaseSynchId = new Integer(0);
        }
    }

    private void incrementDatabaseVersion(String mac_address) throws SQLException {
        databaseStatement.executeUpdate("UPDATE clients_versions SET id_version="+(dataBaseSynchId+1)+" WHERE mac = "+mac_address);
        ++dataBaseSynchId;
    }

    void RunScript(int userId, SqlCommandList batch) {
        try {
            boolean isAdmin = isUserAdmin(userId);

            dbConnection.setAutoCommit(false);

            ListIterator<String> iter = (ListIterator<String>) batch.iterator();
            while (iter.hasNext()) {
               if (isAdmin || isAcceptableTableInQuery(iter.next()))  {
                   databaseStatement.executeUpdate(iter.next());
                   SetToHistory(iter.next(), dataBaseSynchId+1);
               }
            }

            //если все этапы прошли корректно увеличиваем версию
            if (isAdmin) {
                incrementDatabaseVersion(ReportServer.getBluetoothMacAddress());
            }

            dbConnection.commit();

        } catch (SQLException e) {
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

    private boolean isUserAdmin(int userId) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT is_admine FROM users WHERE _id_user = "+userId);
        return rs.getBoolean("is_admine");
    }

    private boolean isAcceptableTableInQuery(String query) {
        String[] words = query.split(" ");
        if ((words[0] == "INSERT") || (words[0] == "DELETE") ) {
            return (words[2] == "\"detour\"");
        }

        if (words[0] == "UPDATE") {
            return (words[1] == "\"detour\"");
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
        ResultSet rs = databaseStatement.executeQuery("SELECT id_user FROM detour WHERE _id_detour = "+idDetour);
        int userId = rs.getInt("id_user");
        rs = databaseStatement.executeQuery("SELECT fio FROM users WHERE _id_user = "+userId);
        return rs.getString("fio");
    }

    public String getRouteNameFromDetourTable(int idDetour) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT id_route FROM detour WHERE _id_detour = "+idDetour);
        int routeId = rs.getInt("id_route");
        rs = databaseStatement.executeQuery("SELECT name FROM routs WHERE _id_route = "+routeId);
        return rs.getString("name");
    }

    public String getStartTimeFromDetourTable(int idDetour) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT time_start FROM detour WHERE _id_detour = "+idDetour);
        return rs.getString("time_start");
    }

    public String getEndTimeFromDetourTable(int idDetour) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT time_stop FROM detour WHERE _id_detour = "+idDetour);
        return rs.getString("time_stop");
    }

    public boolean getStatusFromDetourTable(int idDetour) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT finished FROM detour WHERE _id_detour = "+idDetour);
        return rs.getBoolean("finished");
    }

    public String getRoutesTablePathRoutePicture(int idRoute) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT path_picture_route FROM routs WHERE _id_route = "+idRoute);
        return rs.getString("path_picture_route");
    }

}
