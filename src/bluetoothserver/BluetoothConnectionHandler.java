package reportserver;

import org.apache.log4j.Logger;

import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import java.io.*;
import java.util.Arrays;
import java.util.NoSuchElementException;

import org.json.simple.JSONObject;

class BluetoothConnectionHandler implements Runnable {
    //Размер поточного байтового буфера
    private final int MAX_BUFFER_SIZE = 100*1024;
    private long timeoutStartTime = 0;

    private enum ConnectionState {
        CONNECTION_STATE_WAITING,
        CONNECTION_STATE_OPEN,
        CONNECTION_STATE_CREATE_CONNECTION,
        CONNECTION_STATE_WORKING,
    }

    private BluetoothServer parent;
    private String url;
    private ConnectionState connectionState;
    private StreamConnectionNotifier streamConnNotifier;
    private StreamConnection currentConnection;
    private BufferedInputStream inStream;
    private BufferedOutputStream senderStream;
    private static Logger log = Logger.getLogger(BluetoothConnectionHandler.class);

    BluetoothConnectionHandler(BluetoothServer parent_, String url_) throws NullPointerException {
        if ( (url_ == null) || (parent_ == null) ) {
            NullPointerException criticalExepction = new NullPointerException();
            log.error(criticalExepction);
            throw criticalExepction;
        }

        url = url_;
        parent = parent_;
        connectionState = ConnectionState.CONNECTION_STATE_WAITING;
    }

    void stop() {
        synchronized (connectionState) {
            connectionState = ConnectionState.CONNECTION_STATE_WAITING;
        }
        try {
            if (currentConnection != null)
                currentConnection.close();

            if (streamConnNotifier != null)
            streamConnNotifier.close();

        } catch (IOException e) {
            log.error(e);
        }
    }

    synchronized void start() {
        connectionState = ConnectionState.CONNECTION_STATE_OPEN;
    }

    synchronized  public ConnectionState getConnectionState() {
        return connectionState;
    }

    private StreamConnection createConnection(String url) throws IOException {
        StreamConnection connection = streamConnNotifier.acceptAndOpen();
        RemoteDevice dev = RemoteDevice.getRemoteDevice(connection);
        log.info("Remote device address:"+dev.getBluetoothAddress());
        log.info("Remote device name:"+dev.getFriendlyName(true));
        return connection;
    }

    public void run() {
        while (true) {
            //прибиваем поток
            if (Thread.currentThread().isInterrupted()) {
                stop();
                return;
            }

            switch (connectionState) {
                case CONNECTION_STATE_WAITING: {
                    break;
                }

                case CONNECTION_STATE_OPEN: {
                    try {
                        LocalDevice local = LocalDevice.getLocalDevice();
                        if (local.getDiscoverable() != 0) {
                            local.setDiscoverable(DiscoveryAgent.GIAC);
                        }

                        streamConnNotifier = (StreamConnectionNotifier)Connector.open(url);
                    } catch (IOException e) {
                        synchronized (connectionState) {
                            //TODO: обработать правильно
                            log.warn(e);
                            connectionState = ConnectionState.CONNECTION_STATE_WAITING;
                        }
                        break;
                    }
                    synchronized (connectionState) {
                        connectionState = ConnectionState.CONNECTION_STATE_CREATE_CONNECTION;
                    }
                    break;
                }

                case CONNECTION_STATE_CREATE_CONNECTION: {
                    try {
                        StreamConnection newStreamConnection = createConnection(url);
                        synchronized (connectionState) {
                            currentConnection = newStreamConnection;
                            inStream = new BufferedInputStream(currentConnection.openInputStream());
                            senderStream = new BufferedOutputStream(currentConnection.openOutputStream());
                        }
                    } catch (IOException e1) {
                        log.warn(ConnectionState.CONNECTION_STATE_CREATE_CONNECTION+" FAILED\n"+
                                    "May be bluetooth reader interrupted: "+e1);
                        synchronized (connectionState) {
                            connectionState = ConnectionState.CONNECTION_STATE_WAITING;
                        }
                        break;
                    }

                    synchronized (connectionState) {
                        connectionState = ConnectionState.CONNECTION_STATE_WORKING;
                        refreshTransactionTimeout();
                    }

                    break;
                }

                case CONNECTION_STATE_WORKING: {
                    receiveHandler(currentConnection);
                    sendHandler();

                    if (isTransactionTimeout()) {
                        stop();
                        synchronized (connectionState) {
                            connectionState = ConnectionState.CONNECTION_STATE_OPEN;
                        }
                        log.warn("Transaction timeout");
                    }

                    break;
                }
            }
        }
    }

    private void refreshTransactionTimeout() {
        timeoutStartTime = System.currentTimeMillis()+5000;
    }

    private boolean isTransactionTimeout() {
        return (System.currentTimeMillis() >= timeoutStartTime);
    }

    private void receiveHandler(StreamConnection connection) {
        try {
            BluetoothSimpleTransaction result = dataReceiving(connection);
            parent.pushReceivedTransaction(result);
        } catch (NoSuchElementException e) {
            //Ничего критичного, ждем данные
        }catch (IOException e) {
            log.warn(e);
        }
    }

    private void sendHandler() {
        try {
            BluetoothSimpleTransaction transactionForSend = parent.popSendTransaction();

            //Отправляем заголовок
            try {
                senderStream.write(transactionForSend.getHeader().toJSONString().getBytes());
                senderStream.flush();
                log.info("Send header");
            } catch (IOException e) {
                log.warn(e);
            }

            //Проверяем тип транзакции и отправляем соответствующий типу блок даных
            try {
                BluetoothByteTransaction byteTransaction = (BluetoothByteTransaction) transactionForSend;
                senderStream.write(byteTransaction.getBody());
                senderStream.flush();
                log.info("Send byteTransaction");
                return;
            } catch (IOException e1) {
                log.warn(e1);
            } catch (ClassCastException e2) {

            }

            try {
                BluetoothFileTransaction fileTransaction = (BluetoothFileTransaction) transactionForSend;
                InputStream inputstream = new FileInputStream(fileTransaction.getFileName());
                BufferedInputStream fileReader = new BufferedInputStream(inputstream);
                byte[] buffer = new byte[1024 * 1024];

                int numberOfBytes = 0;
                while (numberOfBytes != (-1)) {
                    numberOfBytes = fileReader.read(buffer);
                    if (numberOfBytes > 0) {
                        senderStream.write(buffer, 0, numberOfBytes);
                        senderStream.flush();
                    }
                }
                log.info("Send fileTransaction");
                return;
            } catch (IOException e) {
                log.warn(e);
            } catch (ClassCastException e2) {

            }
        } catch (NoSuchElementException e) {

        }
    }

    private String initReceivedFileName(StreamConnection connection, String dir) throws IOException {
        FileHandler fileNameHandler = new FileHandler(dir);
        RemoteDevice remote = RemoteDevice.getRemoteDevice(connection);
        return fileNameHandler.generateName(remote.getBluetoothAddress(), "sql");
    }

    private BluetoothSimpleTransaction dataReceiving(StreamConnection connection) throws IOException {

        if (inStream.available() == 0) {
            throw new NoSuchElementException();
        }

        refreshTransactionTimeout();

        String synchDataBaseFile = "base-synchronization";
        String receivedFileName = initReceivedFileName(connection, synchDataBaseFile);
        JSONReceiver receiver = new JSONReceiver();

        byte[] tempBuffer = new byte[MAX_BUFFER_SIZE];
        long transactionTotalSize = 0;
        int byteIndexInFile = 0;

        while (!receiver.isHeaderReceived()) {
            try {
                //read string from spp client
                int bytesRead = inStream.read(tempBuffer, 0, tempBuffer.length);
                int indexLastHeaderByte = receiver.receiveHeader(tempBuffer);

                refreshTransactionTimeout();

                if (receiver.isHeaderReceived()) {
                    JSONObject header = receiver.getHeader();
                    bytesRead-= indexLastHeaderByte+1;
                    boolean isTransactionWithBody = header.containsKey("size");
                    transactionTotalSize = (long) header.get("size");

                    if (isTransactionWithBody && (transactionTotalSize > 0)) {
                        if (bytesRead > 0) {
                            byte[] temp = Arrays.copyOfRange(tempBuffer, (indexLastHeaderByte + 1),
                                                                (indexLastHeaderByte + 1) + bytesRead);

                            FileOutputStream fileOutputStream = new FileOutputStream(synchDataBaseFile + "/" + receivedFileName);
                            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                            bufferedOutputStream.write(temp, byteIndexInFile, bytesRead);
                            bufferedOutputStream.flush();
                            bufferedOutputStream.close();
                            byteIndexInFile += bytesRead;
                        }
                        break;
                    } else {
                        return new BluetoothSimpleTransaction(header);
                    }
                }
            } catch (IOException e) {
                log.error(e);
                throw e;
            }
        }

        while (byteIndexInFile < transactionTotalSize) {
            try {
                int bytesRead = inStream.read(tempBuffer, 0, tempBuffer.length);
                refreshTransactionTimeout();
                FileOutputStream fileOutputStream = new FileOutputStream(synchDataBaseFile + "/" + receivedFileName, true);
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                bufferedOutputStream.write(tempBuffer, 0, bytesRead);
                bufferedOutputStream.flush();
                bufferedOutputStream.close();
                byteIndexInFile += bytesRead;
            } catch (IOException e) {
                log.error(e);
                throw e;
            }
        }

        if (transactionTotalSize == byteIndexInFile) {
            return new BluetoothFileTransaction(receiver.getHeader(), receivedFileName);
        }

        throw new NoSuchElementException();
    }

    private void ErrorCloseConnection() {
        try {
            currentConnection.close();
        }
        catch (IOException e) {
            log.error(e);
        }

        synchronized (connectionState) {
            connectionState = ConnectionState.CONNECTION_STATE_WAITING;
        }
    }

    String getRemoteDeviceBluetoothAddress() throws IOException {
            return RemoteDevice.getRemoteDevice(currentConnection).getBluetoothAddress();
    }
}