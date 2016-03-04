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


public class BluetoothServer extends CommonServer {

    //Размер поточного байтового буфера
    final int MAX_BUFFER_SIZE = 100000;

    private UUID uuid;
    private String name;
    private String url;
    private StreamConnection currentConnection;
    private Thread serverThread;

    BluetoothServer() {
        setState(ServerState.SERVER_NOT_INITIALIZE);
    }

    private StreamConnection createConection(String url) throws IOException {
        StreamConnectionNotifier streamConnNotifier = (StreamConnectionNotifier) Connector.open(url);
        System.out.println("Server Started. Waiting for clients to connect…");
        StreamConnection connection = streamConnNotifier.acceptAndOpen();
        RemoteDevice dev = RemoteDevice.getRemoteDevice(connection);

        System.out.println("Remote device address:"+dev.getBluetoothAddress());
        System.out.println("Remote device name:"+dev.getFriendlyName(true));

        return connection;
    }

    public void init() {
        setState(ServerState.SERVER_INITIALIZING);
        uuid = new UUID("1101", true);
        name = "Echo Server";
        url = "btspp://localhost:" + uuid + ";name=" + name;
        System.out.println("uuid: " + uuid);
        //+ ";authenticate=false;encrypt=false;";

        try {
            currentConnection = createConection(url);
        } catch (IOException e) {
            System.out.println(e);
            return;
        }

        serverThread = new Thread( new ReaderThread() );
        setState(ServerState.SERVER_READY_NOT_ACTIVE);
    }

    private class ReaderThread implements Runnable {
        public void run() {
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
            } catch (IOException e) {
                System.out.println(e);
                return;
            }
        }
    }

    //@FIXME:
    public void stop() throws IOException {
        try {
            currentConnection.close();
        } catch(IOException e) {

        }
        setState(ServerState.SERVER_STOP);
    }

    public void start() throws IOException {
        serverThread.start();
        setState(ServerState.SERVER_ACTIVE);
    }
}
