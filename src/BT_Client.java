import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import java.io.IOException;

/**
 * Created by m on 08.02.16.
 */
public class BT_Client implements DiscoveryListener {




    public void deviceDiscovered(RemoteDevice remoteDevice, DeviceClass deviceClass) {


    }

    public void servicesDiscovered(int i, ServiceRecord[] serviceRecords) {

    }

    public void serviceSearchCompleted(int i, int i1) {

    }

    public void inquiryCompleted(int i) {

    }
}
