import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by m on 16.02.16.
 */
public class BT_Server_Socket {

    void run() throws IOException {

        ServerSocket serverSocket;
        Socket clientSocket;
        InputStream inputStream;
        FileOutputStream fileOutputStream;
        BufferedOutputStream bufferedOutputStream;
        int filesize = 10000000; // filesize temporary hardcoded
        int bytesRead;
        int current = 0;

        serverSocket = new ServerSocket(4444);  //Server socket

        System.out.println("Server started. Listening to the port 4444");


        clientSocket = serverSocket.accept();
        byte[] mybytearray = new byte[filesize];    //create byte array to buffer the file

        inputStream = clientSocket.getInputStream();
        fileOutputStream = new FileOutputStream("output.txt");
        bufferedOutputStream = new BufferedOutputStream(fileOutputStream);

        System.out.println("Receiving...");

        //following lines read the input slide file byte by byte
        bytesRead = inputStream.read(mybytearray, 0, mybytearray.length);
        current = bytesRead;

        do {
            bytesRead = inputStream.read(mybytearray, current, (mybytearray.length - current));
            if (bytesRead >= 0) {
                current += bytesRead;
            }
        } while (bytesRead > -1);


        bufferedOutputStream.write(mybytearray, 0, current);
        bufferedOutputStream.flush();
        bufferedOutputStream.close();
        inputStream.close();
        clientSocket.close();
        serverSocket.close();

        System.out.println("Sever recieved the file");
    }

}
