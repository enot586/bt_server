package reportserver;


import org.json.simple.JSONArray;

public class TestStubWebActionsHandler implements ActionFromWeb {
    @Override
    public void bluetoothServerStart() {

    }

    @Override
    public void bluetoothServerStop() {

    }

    @Override
    public CommonServer.ServerState getStateBluetoothServer() {
        CommonServer.ServerState state = CommonServer.ServerState.SERVER_ACTIVE;
        return state;
    }

    @Override
    public String getBluetoothMacAddress() {
        return "AABBCCDDEEFF";
    }

    @Override
    public JSONArray getUsersList() {
        return new JSONArray();
    }

    @Override
    public JSONArray getRoutesList() {
        return new JSONArray();
    }

    @Override
    public JSONArray getFilteredDetour(int userId, int routeId, int rowNumber,
                                       String startDate1, String startDate2,
                                       String finishDate1, String finishDate2) {
        return new JSONArray();
    }

    @Override
    public JSONArray getVisits(int detourId) {
        return new JSONArray();
    }

    @Override
    public JSONArray getOldUserMessageHandler() {
        return new JSONArray();
    }
}
