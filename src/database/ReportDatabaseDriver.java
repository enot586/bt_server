package reportserver;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import static java.nio.file.StandardCopyOption.*;

public class ReportDatabaseDriver {
    private String url;
    private Statement databaseStatement;
    private Connection dbConnection;

    public void init(String url_) throws SQLException {
        url = url_;

        try {
            Class.forName("org.sqlite.JDBC");

            DriverManager.registerDriver( new org.sqlite.JDBC() );

            dbConnection = DriverManager.getConnection("jdbc:sqlite:"+url);

            if (dbConnection == null) {
                SQLException e = new SQLException();
                throw e;
            }

//            if (conn != null) {
//                System.out.println("Connected to the database");
//                DatabaseMetaData dm = (DatabaseMetaData) conn.getMetaData();
//                System.out.println("Driver name: " + dm.getDriverName());
//                System.out.println("Driver version: " + dm.getDriverVersion());
//                System.out.println("Product name: " + dm.getDatabaseProductName());
//                System.out.println("Product version: " + dm.getDatabaseProductVersion());
//            }

            databaseStatement = dbConnection.createStatement();

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

    public void BackupCurrentDataBase() {
        try {
            databaseStatement.close();
            dbConnection.close();

            URL synchDataBaseFile = ReportServer.class.getClassLoader().getResource("base-synchronization");

            if (synchDataBaseFile == null) {
                //Files.createDirectory("base-synchronization", );
            }

            FileHandler fileHandler = new FileHandler(synchDataBaseFile.getFile());

            File sourceFile = new File(synchDataBaseFile.getFile()+"/"+"app-data.db3");
            File targetFile = new File(synchDataBaseFile.getFile()+"/"+fileHandler.generateNameForDataBase());

            Files.copy( Paths.get(sourceFile.getAbsolutePath()),
                        new FileOutputStream(targetFile));

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void RunScript(SqlCommandList batch) {

    }

    public ArrayList<Integer> getRoutesTableIds() throws SQLException {
        ArrayList<Integer> ids = new  ArrayList<Integer>();

        ResultSet rs = databaseStatement.executeQuery("SELECT _id_route FROM routs");

        int i = 0;
        while ( rs.next() ) {
            int id = rs.getInt("_id_route");
            ids.add(i++, id);
        }
        return ids;
    }

    public String getRoutesTableUser(int idRoute) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT name FROM routs WHERE _id_route = "+idRoute);
        return rs.getString("name");
    }

    public String getRoutesTableDate(int idRoute) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT date_create FROM routs WHERE _id_route = "+idRoute);
        return rs.getString("date_create");
    }

    public int getRoutesTableActuality(int idRoute) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT actuality FROM routs WHERE _id_route = "+idRoute);
        return rs.getInt("actuality");
    }

    public String getRoutesTablePathRoutePicture(int idRoute) throws SQLException {
        ResultSet rs = databaseStatement.executeQuery("SELECT path_picture_route FROM routs WHERE _id_route = "+idRoute);
        return rs.getString("path_picture_route");
    }

}
