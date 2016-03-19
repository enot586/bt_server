package reportserver;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.ListIterator;

public class ReportDatabaseDriver {
    private String url;
    private Statement databaseStatement;
    private Connection dbConnection;

    private int dataBaseSynchId = 0;

    public enum DatabaseState {
        DB_CLOSE,
        DB_OPEN,
        BD_BACKUP
    }

    DatabaseState dbState = DatabaseState.DB_CLOSE;

    public int getDbSynchId() {
        return dataBaseSynchId;
    }

    public void init(String url_) throws SQLException {
        url = url_;

        try {
            Class.forName("org.sqlite.JDBC");

            DriverManager.registerDriver( new org.sqlite.JDBC() );

            dbConnection = DriverManager.getConnection("jdbc:sqlite:"+url);

            if (dbConnection == null)
                throw new SQLException();

            databaseStatement = dbConnection.createStatement();

            synchronized (dbState) {
                dbState = DatabaseState.DB_OPEN;
            }

            ResultSet rs = databaseStatement.executeQuery("SELECT * FROM routs");

            while ( rs.next() ) {
                int supplierID = rs.getInt("_id_route");
                String routesName = rs.getString("name");
                String routesDate = rs.getString("date_create");
                int routesActivity = rs.getInt("actuality");
                String routesPath = rs.getString("path_picture_route");
            }
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    synchronized public DatabaseState getBdState() {
        return dbState;
    }

    public void BackupCurrentDatabase(String uniqPart) {
        synchronized (dbState) {
            dbState = DatabaseState.BD_BACKUP;
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
                dbState = DatabaseState.DB_OPEN;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            synchronized (dbState) {
                dbState = DatabaseState.DB_CLOSE;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            synchronized (dbState) {
                dbState = DatabaseState.DB_CLOSE;
            }
        } catch (IOException e) {
            e.printStackTrace();
            synchronized (dbState) {
                dbState = DatabaseState.DB_CLOSE;
            }
        }
    }

    public void SetHistory(SqlCommandList batch) {
        //TODO: Записать все запросы в историю для текущей таблицы
    }

    public void RestoreBackup() {

    }

    public void RunScript(SqlCommandList batch) {
        ListIterator<String> iter = (ListIterator<String>) batch.iterator();
        while (iter.hasNext()) {
            try {
               databaseStatement.executeUpdate(iter.next());
            } catch(SQLException e) {
                //RestoreBackup();
                //return;
            }
        }

        try {
            databaseStatement.executeUpdate("COMMIT");
        } catch(SQLException e) {
            RestoreBackup();
            return;
        }
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
