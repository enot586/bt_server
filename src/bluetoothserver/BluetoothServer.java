package reportserver;

import org.apache.log4j.Logger;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.UUID;
import java.io.*;
import java.util.LinkedList;
import java.util.NoSuchElementException;


class BluetoothServer extends CommonServer {

    private UUID uuid;
    private String name;
    private String url;
    private Thread serverThread;
    private BluetoothConnectionHandler connectionHandler;
    private final LinkedList<BluetoothSimpleTransaction> receivedTransactionsQueue = new LinkedList<BluetoothSimpleTransaction>();
    private final LinkedList<BluetoothSimpleTransaction> sendTransactionsQueue = new LinkedList<BluetoothSimpleTransaction>();
    private static final Logger log = Logger.getLogger(BluetoothServer.class);

    BluetoothServer() {
        setState(ServerState.SERVER_INITIALIZING);
    }

    synchronized boolean sendData(BluetoothSimpleTransaction transaction) {
        return sendTransactionsQueue.offer(transaction);
    }

    private void createConnectionHandlerThread() throws Exception {
        connectionHandler = new BluetoothConnectionHandler(this, url);
        connectionHandler.start();

        serverThread = new Thread(connectionHandler);
        serverThread.start();
    }

    boolean isReadyToWork() {
        return this.getServerState() != ServerState.SERVER_INITIALIZING;
    }

    void init() throws IOException, Exception {
        setState(ServerState.SERVER_INITIALIZING);
        uuid = new UUID("1101", true);
        name = "Echo Server";
        url = "btspp://localhost:" + uuid + ";name=" + name+ ";authenticate=false;encrypt=false;";
        log.info("Bluetooth server init");
        log.info("uuid: " + uuid);
        setState(ServerState.SERVER_STOPPED);
    }

    synchronized public void stop() throws IOException {
        if (!isReadyToWork()) return;
        setState(ServerState.SERVER_STOPPED);
        log.info("Bluetooth server stop()");
        if (connectionHandler != null) connectionHandler.stop(); //необходимо на случай если сервер ожидает подключения, чтобы вывести его из ожидания
        if (serverThread != null) serverThread.interrupt();
    }

    synchronized public void start() throws Exception {
        if (this.getServerState() == ServerState.SERVER_STOPPED) {
            try {
                createConnectionHandlerThread();
            } catch (Exception e) {
                log.error(e);
                setState(ServerState.SERVER_STOPPED);
                return;
            }
            log.info("Bluetooth server start()");
            setState(ServerState.SERVER_ACTIVE);
        }
    }

    synchronized void pushReceivedTransaction(BluetoothSimpleTransaction receivedTransaction) {
        receivedTransactionsQueue.offer(receivedTransaction);
    }

    synchronized BluetoothSimpleTransaction popReceivedTransaction() throws NoSuchElementException {
        BluetoothSimpleTransaction result;
        result = receivedTransactionsQueue.element();
        receivedTransactionsQueue.remove();
        return result;
    }

    String getRemoteDeviceBluetoothAddress() throws IOException {
        return connectionHandler.getRemoteDeviceBluetoothAddress();
    }

    String getLocalHostMacAddress() {
        try {
            LocalDevice host = LocalDevice.getLocalDevice();
            return host.getBluetoothAddress();
        } catch (BluetoothStateException e) {
            return null;
        }
     }

    synchronized BluetoothSimpleTransaction popSendTransaction() throws NoSuchElementException {
        BluetoothSimpleTransaction result;
        result = sendTransactionsQueue.element();
        sendTransactionsQueue.remove();
        return result;
    }
}
