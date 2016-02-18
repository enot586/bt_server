import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import javax.obex.HeaderSet;
import javax.obex.Operation;
import javax.obex.ResponseCodes;
import javax.obex.ServerRequestHandler;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by m on 09.02.16.
 */
public class BT_Server {

    void start() throws IOException {


        UUID uuid = new UUID("1101", true);
        String name = "Echo Server";
        String url = "btspp://localhost:" + uuid + ";name=" + name;
        System.out.println(uuid.toString());
        //+ ";authenticate=false;encrypt=false;";
        //LocalDevice local = null;
        //StreamConnectionNotifier server = null;
        //StreamConnection conn = null;

        StreamConnectionNotifier streamConnNotifier = (StreamConnectionNotifier) Connector.open(url);
            System.out.println("Server Started. Waiting for clients to connect…");
            StreamConnection connection = streamConnNotifier.acceptAndOpen();
        


            RemoteDevice dev = RemoteDevice.getRemoteDevice(connection);
            System.out.println("Remote device address:"+dev.getBluetoothAddress());
            System.out.println("Remote device name:"+dev.getFriendlyName(true));

//read string from spp client
            InputStream inStream = connection.openInputStream();


        BufferedReader bReader = new BufferedReader(new InputStreamReader(inStream));

        byte [] mba=new byte[100000];
        int bytesRead;
        bytesRead = inStream.read(mba, 0, mba.length);
        FileOutputStream fileOutputStream;
        BufferedOutputStream bufferedOutputStream;

        fileOutputStream = new FileOutputStream("output.txt");
        bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
        bufferedOutputStream.write(mba, 0, bytesRead);
        bufferedOutputStream.flush();
        bufferedOutputStream.close();
        inStream.close();


//send response to spp client
            OutputStream outStream = connection.openOutputStream();
            PrintWriter pWriter = new PrintWriter(new OutputStreamWriter(outStream));
            pWriter.write("Response String from SPP Server\r\n");
            pWriter.flush();

            pWriter.close();
            streamConnNotifier.close();


        }



}
