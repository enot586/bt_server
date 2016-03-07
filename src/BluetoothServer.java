package reportserver;

import javax.bluetooth.UUID;
import java.io.*;


public class BluetoothServer extends CommonServer {

    private UUID uuid;
    private String name;
    private String url;
    private Thread serverThread;
    private BtStreamReader reader;

    BluetoothServer() {
        setState(ServerState.SERVER_INITIALIZING);
    }

    private void createReaderThread() throws Exception {
        reader = new BtStreamReader(url);
        reader.start();

        serverThread = new Thread(reader);
        serverThread.start();
    }

    boolean isReadyToWork() {
        if (getServerState() != ServerState.SERVER_INITIALIZING) return true;
        return false;
    }

    public void init() throws IOException, Exception {
        setState(ServerState.SERVER_INITIALIZING);

        uuid = new UUID("1101", true);
        name = "Echo Server";
        url = "btspp://localhost:" + uuid + ";name=" + name+ ";authenticate=false;encrypt=false;";
        System.out.println("uuid: " + uuid);

        setState(ServerState.SERVER_STOPPED);
    }

    synchronized public void stop() throws IOException {
        if (!isReadyToWork()) return;
        setState(ServerState.SERVER_STOPPED);

        reader.stop(); //необходимо на случай если сервер ожидает подключения, чтобы вывести его из ожидания

        serverThread.interrupt();
    }

    synchronized public void start() throws Exception {
        if ( !isReadyToWork() ) return;

        if (this.getServerState() != ServerState.SERVER_ACTIVE) {
            try {
                createReaderThread();
            } catch (Exception e) {
                System.out.println(e);
                setState(ServerState.SERVER_STOPPED);
                return;
            }
            setState(ServerState.SERVER_ACTIVE);
        }
    }
}
