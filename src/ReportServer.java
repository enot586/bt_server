
import javax.bluetooth.*;
import com.intel.bluetooth.*;
import org.eclipse.jetty.server.Server;
import java.io.IOException;


public class ReportServer implements BlueCoveLocalDeviceProperties{


    public static void main(String[] args) throws IOException, InterruptedException {

        Server server = new Server(8080);
        
        server.setHandler( new HelloWorld() );

        try {
            server.start();
            server.join();
        }
        catch (Exception e)
        {

        }


//        LocalDevice localDevice = null;
//
//        while (true) {
//
//            if (!LocalDevice.isPowerOn()) {
//                System.err.println("Take on Bluetooth");
//                System.out.println("Click any key");
//                System.in.read();
//
//
//            } else {
//                localDevice = localDevice.getLocalDevice();
//
//                break;
//
//            }
//        }
//
//        System.out.println("Address: "+localDevice.getBluetoothAddress());
//        System.out.println("Name: "+localDevice.getFriendlyName());
//
//        BT_Server server=new BT_Server();
//        server.start();

/*
        MyFiles myFiles=new MyFiles();

        int user=1;
        int detour=1;

        myFiles.detour_id=Integer.toString(detour);
        myFiles.user_id=Integer.toString(user);
        myFiles.is_complete=false;

        for (int y=0;y<20;y++){

            myFiles.saveAdd("string"+y);
            if (y==detour*6)

            {
                detour++;
                myFiles.rename(myFiles.make_name());
                myFiles.detour_id=Integer.toString(detour);
            }


        }

*/

        //BT_Client client=new BT_Client();
        //client.serverConnect(localDevice);




    }
}



