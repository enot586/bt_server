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

/**
 * Created by m on 09.02.16.
 */
public class BT_Server {

    void start() throws IOException {

        UUID uuid = new UUID("1101", true);
        String name = "Echo Server";
        String url = "btspp://localhost:" + uuid + ";name=" + name;
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
            String lineRead = bReader.readLine();
            System.out.println(lineRead);

//send response to spp client
            OutputStream outStream = connection.openOutputStream();
            PrintWriter pWriter = new PrintWriter(new OutputStreamWriter(outStream));
            pWriter.write("Response String from SPP Server\r\n");
            pWriter.flush();

            pWriter.close();
            streamConnNotifier.close();
        }


/*
    public BT_Server() {
        try {
            System.out.println("Setting device to be discoverable...");
            local = LocalDevice.getLocalDevice();
            local.setDiscoverable(DiscoveryAgent.GIAC);
            System.out.println("Start advertising service...");
            server = (StreamConnectionNotifier) Connector.open(url);
            System.out.println("Waiting for incoming connection...");
            conn = server.acceptAndOpen();
            System.out.println("Client Connected...");
            DataInputStream din = new DataInputStream(conn.openInputStream());
            while (true) {
                String cmd = "";
                char c;
                while (((c = din.readChar()) > 0) && (c != '\n')) {
                    cmd = cmd + c;
                }
                System.out.println("Received " + cmd);
            }

        } catch (Exception e) {
            System.out.println("Exception Occured: " + e.toString());
        }

    }*/
}
