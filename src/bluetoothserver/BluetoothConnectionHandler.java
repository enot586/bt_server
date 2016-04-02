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
    private long timeoutTime = 0;

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
    private static final Logger log = Logger.getLogger(BluetoothConnectionHandler.class);

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
                        ReportServer.sendUserMessage("Не удается получить доступ к bluetooth");
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
                        }
                    } catch (IOException e1) {
                        log.warn(e1);
                        synchronized (connectionState) {
                            connectionState = ConnectionState.CONNECTION_STATE_WAITING;
                        }
                        break;
                    }

                    synchronized (connectionState) {
                        connectionState = ConnectionState.CONNECTION_STATE_WORKING;
                        refreshTransactionTimeout();
                        ReportServer.sendUserMessage("Соединение установлено");
                    }

                    break;
                }

                case CONNECTION_STATE_WORKING: {
                    receiveHandler(currentConnection);
                    sendHandler();

                    if (isTransactionTimeout()) {
                        reopenNewConnection();
                        ReportServer.sendUserMessage("Таймаут соединения. Ожидаю нового подключения.");
                        log.warn("Transaction timeout");
                    }

                    break;
                }
            }
        }
    }

    public void reopenNewConnection() {
        stop();
        synchronized (connectionState) {
            connectionState = ConnectionState.CONNECTION_STATE_OPEN;
        }
    }

    private void refreshTransactionTimeout() {
        timeoutTime = System.currentTimeMillis()+5000;
    }

    private boolean isTransactionTimeout() {
        return (System.currentTimeMillis() >= timeoutTime);
    }

    private void receiveHandler(StreamConnection connection) {
        try {
            BluetoothSimpleTransaction result = dataReceiving(connection);
            parent.pushReceivedTransaction(result);
        } catch (NoSuchElementException e) {
            //Ничего критичного, ждем данные
        }
    }

    private void sendHandler() {
        try {
            BluetoothSimpleTransaction transactionForSend = parent.popSendTransaction();

            try (BufferedOutputStream senderStream = new BufferedOutputStream(currentConnection.openOutputStream())) {
                //Отправляем заголовок
                try {
                    senderStream.write(transactionForSend.getHeader().toJSONString().getBytes());
                    senderStream.flush();
                    log.info("Send packet :" + transactionForSend.getHeader().toJSONString());
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
                    try (InputStream inputstream = new FileInputStream(fileTransaction.getFileName())) {
                        try (BufferedInputStream fileReader = new BufferedInputStream(inputstream)) {
                            byte[] buffer = new byte[1024 * 1024];
                            int numberOfBytes = 0;
                            while (numberOfBytes != (-1)) {
                                numberOfBytes = fileReader.read(buffer);
                                if (numberOfBytes > 0) {
                                    senderStream.write(buffer, 0, numberOfBytes);
                                    senderStream.flush();
                                }
                            }
                        }
                    } catch (FileNotFoundException e) {
                        log.warn(e);
                    }
                    log.info("Send fileTransaction");
                    return;
                } catch (ClassCastException e2) {

                }
            } catch(IOException e) {
                log.warn(e);
            }
        } catch (NoSuchElementException e) {

        }
    }

    private String initReceivedFileName(StreamConnection connection, String dir) throws IOException {
        FileHandler fileNameHandler = new FileHandler(dir);
        RemoteDevice remote = RemoteDevice.getRemoteDevice(connection);
        return fileNameHandler.generateName(remote.getBluetoothAddress(), "sql");
    }

    private BluetoothSimpleTransaction dataReceiving(StreamConnection connection) {

        try (BufferedInputStream inStream = new BufferedInputStream(currentConnection.openInputStream())) {
            if (inStream.available() == 0) {
                throw new NoSuchElementException();
            }

            refreshTransactionTimeout();

            JSONReceiver receiver = new JSONReceiver();

            byte[] tempBuffer = new byte[MAX_BUFFER_SIZE];
            long transactionTotalSize = 0;
            int byteIndexInFile = 0;
            String receivedFileName = initReceivedFileName(connection, ProjectDirectories.directoryDownloads);

            while (!receiver.isHeaderReceived()) {
                try {
                    //read string from spp client
                    inStream.read(tempBuffer, 0, 1);
                    receiver.receiveHeader(tempBuffer, 1);

                    refreshTransactionTimeout();

                    if (receiver.isHeaderReceived()) {
                        JSONObject header = receiver.getHeader();

                        log.info("receive file:");
                        log.info(header.toString());

                        boolean isTransactionWithBody = header.containsKey("size");
                        if (isTransactionWithBody) {
                            transactionTotalSize = (long) header.get("size");

                            //если принимаем бинарный файл, то присваиваем ему имя которое пришло в тразакции
                            if (header.containsKey("filename")) {
                                //long type = (long) header.get("type");
                                //if (BluetoothPacketType.BINARY_FILE.getId() == type) {
                                String headerFileName = (String) header.get("filename");
                                if (null != headerFileName) receivedFileName = headerFileName;
                                //}
                            }

                            //Перезаписываем файлик если таковой существует
                            try (FileOutputStream fileOutputStream =
                                         new FileOutputStream(ProjectDirectories.directoryDownloads + "/" + receivedFileName)) {
                                fileOutputStream.flush();
                                log.info(receivedFileName);
                            }
                            break;

                        } else {
                            log.info("BluetoothSimpleTransaction");
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
                    int numberOfBytesToTheEnd = ((int) transactionTotalSize - byteIndexInFile);

                    if (inStream.available() > 0) {
                        int bytesRead = inStream.read(tempBuffer, 0,
                                (numberOfBytesToTheEnd > tempBuffer.length) ? tempBuffer.length : numberOfBytesToTheEnd);
                        refreshTransactionTimeout();

                        try (FileOutputStream fileOutputStream =
                                     new FileOutputStream(ProjectDirectories.directoryDownloads + "/" + receivedFileName, true)) {
                            try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream)) {
                                bufferedOutputStream.write(tempBuffer, 0, bytesRead);
                                bufferedOutputStream.flush();
                                byteIndexInFile += bytesRead;
                            } catch (IOException e) {
                                log.warn(e + "can't write to file !");
                            }
                        } catch(FileNotFoundException e) {
                            log.warn(e);
                        }
                    }
                } catch (IOException e) {
                    log.error(e);
                    throw e;
                }
            }

            if (transactionTotalSize == byteIndexInFile) {
                log.info("complete receive:" + receiver.getHeader().toJSONString());
                return new BluetoothFileTransaction(receiver.getHeader(), receivedFileName);
            }
        } catch (IOException e) {
            log.warn(e);
        }
        throw new NoSuchElementException();
    }

    String getRemoteDeviceBluetoothAddress() throws IOException {
            return RemoteDevice.getRemoteDevice(currentConnection).getBluetoothAddress();
    }
}