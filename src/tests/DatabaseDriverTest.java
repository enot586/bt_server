package reportserver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DatabaseDriverTest {

    private final DatabaseDriver db = new DatabaseDriver();
    private final int testDeourNumber = 1;
    private final String testDateString = new String("13.04.2016 23:59:59");

    @Before
    public void setUp() throws Exception {
        db.init(ProjectDirectories.test_commonDatabaseRelativePath,
                ProjectDirectories.test_localDatabaseRelativePath);
    }

    @After
    public void tearDown() throws Exception {
        db.close();
    }

    @Test
    public void getVisits() throws Exception {
        ArrayList<VisitData> testVisits = db.getVisits(testDeourNumber);
        assertEquals( 3, testVisits.size() );

        Iterator<VisitData> it = testVisits.iterator();

        VisitData firstVisit = it.next();
        assertEquals(1, firstVisit._id_visit);
        assertEquals(1, firstVisit.id_detour);
        assertEquals(1, firstVisit.id_point);
        assertEquals(testDateString, firstVisit.time);

        firstVisit = it.next();
        assertEquals(2, firstVisit._id_visit);
        assertEquals(1, firstVisit.id_detour);
        assertEquals(2, firstVisit.id_point);
        assertEquals(testDateString, firstVisit.time);

        firstVisit = it.next();
        assertEquals(3, firstVisit._id_visit);
        assertEquals(1, firstVisit.id_detour);
        assertEquals(3, firstVisit.id_point);
        assertEquals(testDateString, firstVisit.time);
    }

    @Test
    public void getUsersList()throws Exception {
        ArrayList<UserData> testUsers = db.getUsersList();
        assertEquals( 1, testUsers.size() );

        Iterator<UserData> it = testUsers.iterator();

        UserData firstUser = it.next();
        assertEquals(1, firstUser._id_user);
        assertEquals(1, firstUser.id_position);
        assertEquals("test-user", firstUser.fio);
    }

    @Test
    public void getRoutesList() throws Exception {
        ArrayList<RouteData> testRoute = db.getRoutesList();
        assertEquals( 1, testRoute.size() );

        Iterator<RouteData> it = testRoute.iterator();

        RouteData firstRoute = it.next();
        assertEquals(1, firstRoute._id_route);
        assertEquals("test-route", firstRoute.name);

    }

    @Test
    public void getFilteredDetour() throws Exception {

    }

    @Test
    public void getPointDescription() throws Exception {
        assertEquals( "description1", db.getPointDescription(1) );
        assertEquals( "description2", db.getPointDescription(2) );
        assertEquals( "description3", db.getPointDescription(3) );
    }

    @Test
    public void init() throws Exception {
        //нетестопригодный
    }

    @Test
    public void close() throws Exception {
        //нетестопригодный
    }

    @Test
    public void getBdState() throws Exception {

    }

    @Test
    public void backupCurrentDatabase() throws Exception {
        //нетестопригодный
    }

    @Test
    public void runScript() throws Exception {
        //нетестопригодный
    }

    @Test
    public void replaceCommonBase() throws Exception {
        //нетестопригодный
    }

    @Test
    public void isUserAdmin() throws Exception {
        assertTrue( db.isUserAdmin(1) );
    }

    @Test
    public void isUserAcceptableTableInQuery() throws Exception {
        assertFalse( db.isUserAcceptableTableInQuery("INSERT INTO users (fio, id_position, is_admin, user_in_system, user_in_archive)"+
                                                    "VALUES ('user', 2, 0, 0, 0)" ));
        assertTrue( db.isUserAcceptableTableInQuery("INSERT INTO detour (id_user, id_route, id_shedule, time_start, time_stop, finished, send)"+
                                                    "VALUES (1, 1, 1, '2016-04-14 23:59:59','2016-04-14 23:59:59', 1, 0)" ));
        assertTrue( db.isUserAcceptableTableInQuery("INSERT INTO visits (_id_visit, id_point, id_detour, time)"+
                                                    "VALUES (1, 1, 1, '2016-04-14 23:59:59')" ));
    }

    @Test
    public void checkClientVersion() throws Exception {
        assertEquals( 3, db.checkClientVersion("AABBCCDDEEFF") );
    }

    @Test
    public void setClientVersion() throws Exception {
        int currentVersion = db.checkClientVersion("AABBCCDDEEFF");
        db.setClientVersion("AABBCCDDEEFF", 6);
        assertEquals( 6, db.checkClientVersion("AABBCCDDEEFF") );
        db.setClientVersion("AABBCCDDEEFF", currentVersion);
        assertEquals( currentVersion, db.checkClientVersion("AABBCCDDEEFF") );
    }

    @Test
    public void getClientHistory() throws Exception {

    }

    @Test
    public void getClientPicturesHistory() throws Exception {

    }

    @Test
    public void getDatabaseVersion() throws Exception {

    }

    @Test
    public void setToHistory() throws Exception {

    }

    @Test
    public void setFileToHistory() throws Exception {

    }

    @Test
    public void getDatabaseVersion1() throws Exception {

    }

    @Test
    public void incrementDatabaseVersion() throws Exception {

    }

    @Test
    public void initDatabaseVersion() throws Exception {

    }

    @Test
    public void getUserMessageNumber() throws Exception {

    }

    @Test
    public void getUserMessages() throws Exception {

    }

    @Test
    public void getUserMessagesDate() throws Exception {

    }

    @Test
    public void addUserMessageToDatabase() throws Exception {

    }

    @Test
    public void getUserName() throws Exception {

    }

    @Test
    public void getRouteName() throws Exception {

    }

    @Test
    public void getRoutesTablePathRoutePicture() throws Exception {

    }

    @Test
    public void addFileToHistory() throws Exception {

    }

    @Test
    public void removeLocalHistory() throws Exception {

    }

    @Test
    public void getPictures() throws Exception {

    }
}