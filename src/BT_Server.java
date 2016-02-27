//import javax.bluetooth.DiscoveryAgent;
//import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
//import javax.microedition.io.SocketConnection;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
//import javax.obex.HeaderSet;
//import javax.obex.Operation;
//import javax.obex.ResponseCodes;
//import javax.obex.ServerRequestHandler;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;


public class BT_Server {

    //Размер поточного байтового буфера
    final int MAX_BUFFER_SIZE = 100000;

    private StreamConnection currentConnection;

    private StreamConnection createConection(String url) throws IOException {
        StreamConnectionNotifier streamConnNotifier = (StreamConnectionNotifier) Connector.open(url);
        System.out.println("Server Started. Waiting for clients to connect…");
        StreamConnection connection = streamConnNotifier.acceptAndOpen();
        RemoteDevice dev = RemoteDevice.getRemoteDevice(connection);

        System.out.println("Remote device address:"+dev.getBluetoothAddress());
        System.out.println("Remote device name:"+dev.getFriendlyName(true));

        return connection;
    }

    void start() throws IOException {
        UUID uuid = new UUID("1101", true);
        String name = "Echo Server";
        String url = "btspp://localhost:" + uuid + ";name=" + name;
        System.out.println("uuid: " + uuid);
        //+ ";authenticate=false;encrypt=false;";

        try {
            currentConnection = createConection(url);
        } catch (IOException e) {
            System.out.println(e);
            return;
        }

        try {
            //read string from spp client
            InputStream inStream = currentConnection.openInputStream();

            BufferedReader bReader = new BufferedReader(new InputStreamReader(inStream));

            byte[] mba = new byte[MAX_BUFFER_SIZE];
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
        } catch (IOException e) {
            System.out.println(e);
            return;
        }

        try {
            //send response to spp client
            OutputStream outStream = currentConnection.openOutputStream();
            PrintWriter pWriter = new PrintWriter(new OutputStreamWriter(outStream));
            pWriter.write("Response String from SPP Server\r\n");
            pWriter.flush();

            pWriter.close();

            currentConnection.close();
        } catch (IOException e) {
            System.out.println(e);
            return;
        }
    }
}
