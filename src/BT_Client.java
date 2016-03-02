import javax.bluetooth.*;
import java.io.IOException;


public class BT_Client {

    public void serverConnect(LocalDevice localDevice) throws BluetoothStateException, InterruptedException {
        final Object inquiryCompletedEvent = new Object();
        localDevice.setDiscoverable(DiscoveryAgent.GIAC);

        DiscoveryListener listener = new DiscoveryListener() {

            public void deviceDiscovered(RemoteDevice remoteDevice, DeviceClass deviceClass) {
                System.out.println("Device " + remoteDevice.getBluetoothAddress() + " found");

                // devicesDiscovered.addElement(btDevice);
                try {
                    System.out.println("     name " + remoteDevice.getFriendlyName(false));
                } catch (IOException cantGetDeviceName) {
                }

            }

            public void servicesDiscovered(int i, ServiceRecord[] serviceRecords) {

            }

            public void serviceSearchCompleted(int i, int i1) {


            }

            public void inquiryCompleted(int i) {
                System.out.println("Device Inquiry completed!");
                synchronized (inquiryCompletedEvent) {

                    inquiryCompletedEvent.notifyAll();
                }
            }
        };

        synchronized (inquiryCompletedEvent) {
            boolean started = LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, listener);
            if (started) {
                System.out.println("wait for device inquiry to complete...");
                inquiryCompletedEvent.wait();
                System.out.println(" device(s) found");
            }
        }
    }
}
