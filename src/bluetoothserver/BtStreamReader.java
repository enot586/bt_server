package reportserver;

import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import java.io.*;
import java.net.URL;

public class BtStreamReader implements Runnable {
    //Размер поточного байтового буфера
    final int MAX_BUFFER_SIZE = 100*1024;

    public enum ConnectionState {
        CONNECTION_STATE_WAITING,
        CONNECTION_STATE_OPEN,
        CONNECTION_STATE_CREATE_CONNECTION,
        CONNECTION_STATE_OPEN_STREAM,
    }

    private BluetoothServer parent;
    private String url;
    private ConnectionState connectionState;
    private StreamConnectionNotifier streamConnNotifier;
    private StreamConnection currentConnection;

    BtStreamReader(BluetoothServer parent_, String url_) throws NullPointerException {

        if ( (url_ == null) || (parent_ == null) ) {
            NullPointerException criticalExepction = new NullPointerException();
            throw criticalExepction;
        }

        url = url_;
        parent = parent_;
        connectionState = ConnectionState.CONNECTION_STATE_WAITING;
    }

    public void stop() {
        synchronized (connectionState) {
            connectionState = ConnectionState.CONNECTION_STATE_WAITING;
        }
        try {
            if (currentConnection != null)
                currentConnection.close();

            if (streamConnNotifier != null)
            streamConnNotifier.close();

        } catch (IOException e) {
            return;
        }
    }

    synchronized public void start() {
        connectionState = ConnectionState.CONNECTION_STATE_OPEN;
    }

    synchronized  public ConnectionState getConnectionState() {
        return connectionState;
    }

    private StreamConnection createConnection(String url) throws IOException {
        StreamConnection connection = streamConnNotifier.acceptAndOpen();
        RemoteDevice dev = RemoteDevice.getRemoteDevice(connection);

        System.out.println("Remote device address:"+dev.getBluetoothAddress());
        System.out.println("Remote device name:"+dev.getFriendlyName(true));

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
                        LocalDevice local= LocalDevice.getLocalDevice();
                        if (local.getDiscoverable() != 0) {
                            local.setDiscoverable(DiscoveryAgent.GIAC);
                        }

                        streamConnNotifier = (StreamConnectionNotifier)Connector.open(url);;
                    } catch (IOException e) {
                        synchronized (connectionState) {
                            //TODO: обработать правильно
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
                        System.out.println("Server Started. Waiting for clients to connect…");
                    } catch (IOException e1) {
                        //TODO: обработать правильно
                        System.out.println("Server interrupted: " + e1);
                        synchronized (connectionState) {
                            connectionState = ConnectionState.CONNECTION_STATE_WAITING;
                        }
                        break;
                    }
                    synchronized (connectionState) {
                        connectionState = ConnectionState.CONNECTION_STATE_OPEN_STREAM;
                    }
                    break;
                }

                case CONNECTION_STATE_OPEN_STREAM: {
                    try {
                        //read string from spp client
                        InputStream inStream = currentConnection.openInputStream();

                        BufferedReader bReader = new BufferedReader( new InputStreamReader(inStream) );

                        byte[] mba = new byte[MAX_BUFFER_SIZE];
                        int bytesRead;
                        bytesRead = inStream.read(mba, 0, mba.length);

                        FileOutputStream fileOutputStream;
                        BufferedOutputStream bufferedOutputStream;

                        URL synchDataBaseFile = this.getClass().getClassLoader().getResource("");

                        FileHandler   fileNameHandler = new FileHandler( synchDataBaseFile.getFile() );

                        RemoteDevice remote = RemoteDevice.getRemoteDevice(currentConnection);
                        String receivedFileName =
                                fileNameHandler.generateName( remote.getBluetoothAddress(), "sql" );

                        fileOutputStream = new FileOutputStream(synchDataBaseFile.getPath()+"/"+receivedFileName);

                        bufferedOutputStream = new BufferedOutputStream(fileOutputStream);

                        bufferedOutputStream.write(mba, 0, bytesRead);

                        bufferedOutputStream.flush();
                        bufferedOutputStream.close();

                        inStream.close();

                        parent.pushReceiveFileName(receivedFileName);
                    } catch (IOException e) {
                        System.out.println(e);
                        synchronized (connectionState) {
                            connectionState = ConnectionState.CONNECTION_STATE_WAITING;
                        }
                        break;
                    }

                    try {
                        //send response to spp client
                        OutputStream outStream = currentConnection.openOutputStream();
                        PrintWriter pWriter = new PrintWriter(new OutputStreamWriter(outStream));
                        pWriter.write("Response String from SPP Server\r\n");
                        pWriter.flush();
                        pWriter.close();

                        currentConnection.close();

                        //переходим к ожиданию следующего сеанса
                        synchronized (connectionState) {
                            connectionState = ConnectionState.CONNECTION_STATE_CREATE_CONNECTION;
                        }
                    } catch (IOException e) {
                        System.out.println(e);
                        synchronized (connectionState) {
                            connectionState = ConnectionState.CONNECTION_STATE_WAITING;
                        }
                        break;
                    }
                    break;
                }
            }
        }
    }

    String getRemoteDeviceBluetoothAddress() throws IOException {
            return RemoteDevice.getRemoteDevice(currentConnection).getBluetoothAddress();
    }
}