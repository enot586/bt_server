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
    private CommonUserInterface ui;
    //TODO: орагнизовать список, для поддержки нескольких соединений
    private BluetoothConnectionHandler connectionHandler;

    private final LinkedList<BluetoothSimpleTransaction> receivedTransactionsQueue = new LinkedList<BluetoothSimpleTransaction>();
    private final LinkedList<BluetoothSimpleTransaction> sendTransactionsQueue = new LinkedList<BluetoothSimpleTransaction>();
    private static final Logger log = Logger.getLogger(BluetoothServer.class);

    BluetoothServer(CommonUserInterface ui_) {
        ui = ui_;
        setState(ServerState.SERVER_INITIALIZING);
    }

    synchronized boolean sendData(BluetoothSimpleTransaction t) {
        return sendTransactionsQueue.offer(t);
    }

    synchronized boolean sendData(GroupTransaction t) {
        try {
            BluetoothSimpleTransaction first = t.getFirst();
            if (sendTransactionsQueue.offer(first)) {
                t.initSendingProcess();
                t.remove();
                return true;
            }
        } catch (NoSuchElementException e) {
        }
        return false;
    }

    private void createConnectionHandlerThread() throws Exception {
        connectionHandler = new BluetoothConnectionHandler(this, url, ui);
        connectionHandler.start();

        serverThread = new Thread(connectionHandler);
        serverThread.start();
    }

    private boolean isReadyToWork() {
        return this.getServerState() != ServerState.SERVER_INITIALIZING;
    }

    void init() throws IOException, Exception {
        setState(ServerState.SERVER_INITIALIZING);
        uuid = new UUID("1101", true);
        name = "ReportServer";
        url = "btspp://localhost:" + uuid + ";name=" + name + ";authenticate=false;encrypt=false;";
        log.info("Bluetooth server init");
        log.info("uuid: " + uuid);
        setState(ServerState.SERVER_STOPPED);
    }

    void reopenNewConnection() {
        //todo: подумать как обслуживать несколько подключений(несколько BluetoothConnectionHandler)
        connectionHandler.reopenNewConnection();
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

    synchronized BluetoothSimpleTransaction getFirstReceivedTransaction() throws NoSuchElementException {
        return receivedTransactionsQueue.element();
    }

    synchronized void removeFirstReceivedTransaction() throws NoSuchElementException {
        receivedTransactionsQueue.remove();
    }

    synchronized BluetoothSimpleTransaction popReceivedTransaction() throws NoSuchElementException {
        BluetoothSimpleTransaction result;
        result = receivedTransactionsQueue.element();
        receivedTransactionsQueue.remove();
        return result;
    }

    synchronized BluetoothSimpleTransaction popSendTransaction() throws NoSuchElementException {
        BluetoothSimpleTransaction result;
        result = sendTransactionsQueue.element();
        sendTransactionsQueue.remove();
        return result;
    }

    public String getRemoteDeviceBluetoothAddress() throws IOException {
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

    synchronized BluetoothSimpleTransaction getFirstSendTransaction() throws NoSuchElementException {
        return sendTransactionsQueue.element();
    }

    synchronized void removeFirstSendTransaction() throws NoSuchElementException {
        sendTransactionsQueue.remove();
    }

    public int getConnectionId(/*BluetoothConnectionHandler connectionHandler_*/) {
        return /*connectionHandler_*/connectionHandler.getConnectionId();
    }
}
