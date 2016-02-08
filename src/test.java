/**
 * Created by m on 05.02.16.
 */

import javax.bluetooth.*;
import com.intel.bluetooth.*;
import javax.management.*;
import java.io.IOException;


public class test implements BlueCoveLocalDeviceProperties{


    public static void main(String[] args) throws IOException, InterruptedException {
        //System.out.println(LocalDevice.getLocalDevice().getBluetoothAddress());
        LocalDevice localDevice = null;
        while (true) {
            if (!LocalDevice.isPowerOn()) {
                System.err.println("Take on Bluetooth");
                System.out.println("Click any key");
                System.in.read();


            } else {
                localDevice = localDevice.getLocalDevice();

                break;

            }
        }

        localDevice.setDiscoverable(DiscoveryAgent.GIAC);

        final Object inquiryCompletedEvent = new Object();


        DiscoveryListener listener = new DiscoveryListener() {

            public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
                System.out.println("Device " + btDevice.getBluetoothAddress() + " found");
                // devicesDiscovered.addElement(btDevice);
                try {
                    System.out.println("     name " + btDevice.getFriendlyName(false));
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

        synchronized (inquiryCompletedEvent){
            boolean started = LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, listener);
            if (started) {
                System.out.println("wait for device inquiry to complete...");
                inquiryCompletedEvent.wait();
                System.out.println(" device(s) found");
            }
        }


    }
}



