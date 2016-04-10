package reportserver;

import org.apache.log4j.Logger;

import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import java.io.*;
import java.util.NoSuchElementException;

import org.json.simple.JSONObject;

class BluetoothConnectionHandler implements Runnable {
    //Размер поточного байтового буфера
    private final int MAX_BUFFER_SIZE = 100*1024;
    private long timeoutTime = 0;
    private CommonUserInterface ui;

    private enum ConnectionState {
        CONNECTION_STATE_OPEN,
        CONNECTION_STATE_CREATE_CONNECTION,
        CONNECTION_STATE_WORKING,
    }

    private BluetoothServer parent;
    private String url;
    private ConnectionState connectionState;
    private StreamConnectionNotifier clientSession;
    private StreamConnection currentConnection;

    BufferedInputStream inStream;
    BufferedOutputStream outStream;


    private static final Logger log = Logger.getLogger(BluetoothConnectionHandler.class);

    BluetoothConnectionHandler(BluetoothServer parent_, String url_, CommonUserInterface ui_) throws NullPointerException {
        if ( (url_ == null) || (parent_ == null) ) {
            NullPointerException criticalExepction = new NullPointerException();
            log.error(criticalExepction);
            throw criticalExepction;
        }

        ui = ui_;
        url = url_;
        parent = parent_;
        connectionState = ConnectionState.CONNECTION_STATE_OPEN;
    }

    synchronized void stop() {
        try {
            inStream.close();
        } catch (NullPointerException | IOException e) {
        }

        try {
            outStream.close();
        }catch (NullPointerException | IOException e) {
        }

        try {
            currentConnection.close();
        } catch (NullPointerException | IOException e) {
        }

        try {
            clientSession.close();
        } catch (NullPointerException | IOException e) {
            log.warn("Can't close clientSession:"+e);
        }

        connectionState = ConnectionState.CONNECTION_STATE_OPEN;
    }

    synchronized void start() {
        connectionState = ConnectionState.CONNECTION_STATE_OPEN;
    }

    synchronized  public ConnectionState getConnectionState() {
        return connectionState;
    }

    private StreamConnection createConnection(String url) throws IOException {
        StreamConnection connection = clientSession.acceptAndOpen();
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
                case CONNECTION_STATE_OPEN: {
                    try {
                        LocalDevice local = LocalDevice.getLocalDevice();
                        if (local.getDiscoverable() != DiscoveryAgent.GIAC) {
                            local.setDiscoverable(DiscoveryAgent.GIAC);
                        }

                        clientSession = (StreamConnectionNotifier)Connector.open(url, Connector.READ_WRITE);

                        synchronized (connectionState) {
                            connectionState = ConnectionState.CONNECTION_STATE_CREATE_CONNECTION;
                        }
                    } catch (NullPointerException | IOException e) {
                    }

                    break;
                }

                case CONNECTION_STATE_CREATE_CONNECTION: {
                    try {
                        StreamConnection newStreamConnection = createConnection(url);
                        synchronized (connectionState) {
                            currentConnection = newStreamConnection;
                            inStream = new BufferedInputStream(currentConnection.openInputStream());
                            outStream = new BufferedOutputStream(currentConnection.openOutputStream());

                            synchronized (connectionState) {
                                connectionState = ConnectionState.CONNECTION_STATE_WORKING;
                                refreshTransactionTimeout();
                                ui.sendUserMessage("Соединение установлено");
                            }
                        }
                    } catch (IOException e1) {
                        log.warn(e1);
                        reopenNewConnection();
                    }
                    break;
                }

                case CONNECTION_STATE_WORKING: {
                    receiveHandler(inStream);
                    sendHandler(outStream);

                    if (isTransactionTimeout()) {
                        reopenNewConnection();
                        ui.sendUserMessage("Таймаут соединения. Ожидаю нового подключения.");
                        log.warn("Transaction timeout");
                    }

                    break;
                }
            }
        }
    }

    public void reopenNewConnection() {
        stop();
    }

    private void refreshTransactionTimeout() {
        timeoutTime = System.currentTimeMillis()+5000;
    }

    private boolean isTransactionTimeout() {
        return (System.currentTimeMillis() >= timeoutTime);
    }

    synchronized private void receiveHandler(BufferedInputStream receiverStream) {
        try {
            BluetoothSimpleTransaction result = dataReceiving(receiverStream);
            parent.pushReceivedTransaction(result);
        } catch (NoSuchElementException e) {
            //Ничего критичного, ждем данные
        }
    }

    synchronized private void sendHandler(BufferedOutputStream senderStream) {
        try {
            BluetoothSimpleTransaction transactionForSend = parent.popSendTransaction();

            //Отправляем заголовок
            try {
                senderStream.write(transactionForSend.getHeader().toJSONString().getBytes());
                senderStream.flush();

                log.info("Send packet :" + transactionForSend.getHeader().toJSONString());
            } catch (IOException e) {
                log.warn(e);
                return;
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
                return;
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
                    return;
                }
                log.info("Send fileTransaction");
                return;
            } catch (ClassCastException e2) {

            }

        } catch (IOException | NoSuchElementException e) {

        }
    }

    private String initReceivedFileName(String uniqPart, String dir) throws IOException {
        FileHandler fileNameHandler = new FileHandler(dir);
        return fileNameHandler.generateName(uniqPart, "tmp");
    }

    private BluetoothSimpleTransaction dataReceiving(BufferedInputStream receiverStream) throws NoSuchElementException {
        try {
            if (receiverStream.available() == 0) {
                throw new NoSuchElementException();
            }

            refreshTransactionTimeout();

            JSONReceiver receiver = new JSONReceiver();

            byte[] tempBuffer = new byte[MAX_BUFFER_SIZE];
            long transactionTotalSize = 0;
            int byteIndexInFile = 0;
            String receivedFileName = initReceivedFileName(getRemoteDeviceBluetoothAddress(), ProjectDirectories.directoryDownloads);

            while (!receiver.isHeaderReceived()) {
                try {
                    if (isTransactionTimeout()) {
                        break;
                    }

                    if (receiverStream.available() > 0) {
                        //read string from spp client
                        receiverStream.read(tempBuffer, 0, 1);
                        receiver.receiveHeader(tempBuffer, 1);
                    }

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
                    if (isTransactionTimeout()) {
                        break;
                    }

                    int numberOfBytesToTheEnd = ((int) transactionTotalSize - byteIndexInFile);

                    if (receiverStream.available() > 0) {
                        int bytesRead = receiverStream.read(tempBuffer, 0,
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
                        } catch (FileNotFoundException e) {
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