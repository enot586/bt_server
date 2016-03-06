package reportserver;

import javax.bluetooth.RemoteDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import java.io.*;

public class BtStreamReader implements Runnable {
    //Размер поточного байтового буфера
    final int MAX_BUFFER_SIZE = 100000;

    public enum ConnectionState {
        CONNECTION_STATE_WAITING,
        CONNECTION_STATE_OPEN,
        CONNECTION_STATE_CREATE_CONNECTION,
        CONNECTION_STATE_OPEN_STREAM,
    }

    private String url;
    private ConnectionState connectionState;
    private StreamConnectionNotifier streamConnNotifier;
    private StreamConnection currentConnection;

    BtStreamReader(String url_) {
        url = url_;
        connectionState = ConnectionState.CONNECTION_STATE_WAITING;
    }

    synchronized public void stop() {
        connectionState = ConnectionState.CONNECTION_STATE_WAITING;

        try {
            currentConnection.close();
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

    synchronized public void run() {
        switch (connectionState) {
            case CONNECTION_STATE_WAITING: {
                break;
            }

            case CONNECTION_STATE_OPEN: {
                try {
                    streamConnNotifier = (StreamConnectionNotifier) Connector.open(url);
                } catch (IOException e) {
                    //@TODO: обработать правильно
                    connectionState = ConnectionState.CONNECTION_STATE_WAITING;
                    return;
                }

                connectionState = ConnectionState.CONNECTION_STATE_CREATE_CONNECTION;
                break;
            }

            case CONNECTION_STATE_CREATE_CONNECTION: {
                try {
                    currentConnection = createConnection(url);
                    System.out.println("Server Started. Waiting for clients to connect…");
                } catch (IOException e1) {
                    //@TODO: обработать правильно
                    System.out.println("Server interrupted: "+e1);
                    connectionState = ConnectionState.CONNECTION_STATE_WAITING;
                    return;
                }

                connectionState = ConnectionState.CONNECTION_STATE_OPEN_STREAM;
                break;
            }

            case CONNECTION_STATE_OPEN_STREAM: {
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
                    connectionState = ConnectionState.CONNECTION_STATE_WAITING;
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

                    //переходим к ожиданию следующего сеанса
                    connectionState = ConnectionState.CONNECTION_STATE_CREATE_CONNECTION;
                } catch (IOException e) {
                    System.out.println(e);
                    connectionState = ConnectionState.CONNECTION_STATE_WAITING;
                    return;
                }
                break;
            }
        }
    }
}