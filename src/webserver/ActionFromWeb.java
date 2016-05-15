package reportserver;

import org.json.simple.JSONArray;

import javax.bluetooth.BluetoothStateException;

public interface ActionFromWeb {
    void bluetoothServerStart();
    void bluetoothServerStop();
    CommonServer.ServerState getStateBluetoothServer();
    String getBluetoothMacAddress();

    JSONArray getUsersList();
    JSONArray getRoutesList();
    JSONArray getFilteredDetour(int userId, int routeId, int rowNumber, String startDate1,
                                String startDate2, String finishDate1, String finishDate2);

    JSONArray getVisits(int detourId);

    JSONArray getOldUserMessageHandler();
}
