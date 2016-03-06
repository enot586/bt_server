//import javax.bluetooth.DiscoveryAgent;
//import javax.bluetooth.LocalDevice;
import javax.bluetooth.UUID;
//import javax.microedition.io.SocketConnection;
import javax.microedition.io.StreamConnection;
//import javax.obex.HeaderSet;
//import javax.obex.Operation;
//import javax.obex.ResponseCodes;
//import javax.obex.ServerRequestHandler;
import java.io.*;


public class BluetoothServer extends CommonServer {

    private UUID uuid;
    private String name;
    private String url;
    private Thread serverThread;
    private BtStreamReader reader;

    BluetoothServer() {
        setState(ServerState.SERVER_NOT_INITIALIZE);
    }

    public void init() throws IOException, Exception {
        setState(ServerState.SERVER_INITIALIZING);

        uuid = new UUID("1101", true);
        name = "Echo Server";
        url = "btspp://localhost:" + uuid + ";name=" + name;
        System.out.println("uuid: " + uuid);
        //+ ";authenticate=false;encrypt=false;";

        try {
            reader = new BtStreamReader(url);
            serverThread = new Thread(reader);
            serverThread.start();
        } catch (Exception e) {
            System.out.println(e);
            setState(ServerState.SERVER_NOT_INITIALIZE);
            throw e;
        }

        setState(ServerState.SERVER_READY_NOT_ACTIVE);
    }

    synchronized public void stop() throws IOException {
        setState(ServerState.SERVER_STOP);
        reader.stop();
    }

    synchronized public void start() throws IOException {
        if (this.getServerState() != ServerState.SERVER_ACTIVE) {
            setState(ServerState.SERVER_ACTIVE);
            reader.start();
        }
    }
}
