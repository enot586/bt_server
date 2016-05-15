package reportserver;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;

class WebActionsHandler implements ActionFromWeb {
    private BluetoothServer btServer;
    private DatabaseDriver dbDriver;
    private static Logger log = Logger.getLogger(WebActionsHandler.class);

    WebActionsHandler(BluetoothServer bluetoothServer_, DatabaseDriver databaseDriver_) {
        btServer = bluetoothServer_;
        dbDriver = databaseDriver_;
    }

    @Override
    public void bluetoothServerStart() {
        try {
            btServer.start();
        } catch(Exception e) {
            log.error(e);
        }
    }

    @Override
    public void bluetoothServerStop() {
        try {
            btServer.stop();
        } catch(Exception e) {
            log.error(e);
        }
    }

    @Override
    public CommonServer.ServerState getStateBluetoothServer() {
        return btServer.getServerState();
    }

    @Override
    public String getBluetoothMacAddress() {
        return btServer.getLocalHostMacAddress();
    }

    @Override
    public JSONArray getUsersList() {
        ArrayList<UserData> users = dbDriver.getUsersList();
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

    @Override
    public JSONArray getRoutesList() {
        ArrayList<RouteData> routes = dbDriver.getRoutesList();
        JSONArray result = new JSONArray();

        for( RouteData i : routes) {
            JSONObject route = new JSONObject();

            route.put("id", i._id_route);
            route.put("name", i.name);

            result.add(route);
        }

        return result;
    }

    @Override
    public JSONArray getFilteredDetour(int userId, int routeId, int rowNumber, String startDate1, String startDate2, String finishDate1, String finishDate2) {
        ArrayList<DetourData> detour = dbDriver.getFilteredDetour(userId, routeId, rowNumber,
                startDate1, startDate2,
                finishDate1, finishDate2);
        JSONArray result = new JSONArray();

        for( DetourData i : detour) {
            JSONObject detourRow = new JSONObject();

            detourRow.put("_id_detour", i._id_detour);

            String user_name = dbDriver.getUserName(i.id_user);
            detourRow.put("user_name", user_name);

            String route_name = dbDriver.getRouteName(i.id_route);
            detourRow.put("route_name", route_name);

            detourRow.put("start_time", i.time_start);
            detourRow.put("end_time", i.time_stop);
            result.add(detourRow);
        }

        return result;
    }

    @Override
    public JSONArray getVisits(int detourId) {
        ArrayList<VisitData> visits = dbDriver.getVisits(detourId);
        JSONArray result = new JSONArray();

        for( VisitData i : visits) {
            JSONObject detourRow = new JSONObject();

            detourRow.put("_id_visit", i._id_visit);
            detourRow.put("id_point", i.id_point);
            detourRow.put("id_detour", detourId);
            detourRow.put("time", i.time);
            detourRow.put("description", i.description);

            result.add(detourRow);
        }

        return result;
    }

    @Override
    public JSONArray getOldUserMessageHandler() {
        String[] messages = dbDriver.getUserMessages();
        String[] dates = dbDriver.getUserMessagesDate();

        JSONArray fullresponse = new JSONArray();

        for(int i = 0; i < messages.length; ++i) {
            if ((dates[i] != null) && (messages[i]!= null)) {
                JSONObject responseJson = new JSONObject();
                responseJson.put("date", dates[i]);
                responseJson.put("text", messages[i]);

                fullresponse.add(responseJson);
            }
        }

        return fullresponse;
    }
}