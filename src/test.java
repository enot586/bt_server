/**
 * Created by m on 05.02.16.
 */

import javax.bluetooth.*;
import com.intel.bluetooth.*;
import javax.management.*;
import java.io.IOException;


public class test implements BlueCoveLocalDeviceProperties{


    public static void main(String[] args) throws IOException, InterruptedException {


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

        System.out.println("Address: "+localDevice.getBluetoothAddress());
        System.out.println("Name: "+localDevice.getFriendlyName());

        BT_Server server=new BT_Server();
        server.start();


        //BT_Client client=new BT_Client();
        //client.serverConnect(localDevice);




    }
}



