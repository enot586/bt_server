
import java.io.IOException;
import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.LocalDevice;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class TabSynchronize extends HttpServlet
{

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        try {
            LocalDevice localDevice = LocalDevice.getLocalDevice();

            System.out.println("Bluetooth module power: "+localDevice.isPowerOn());
            System.out.println("Address: "+localDevice.getBluetoothAddress());
            System.out.println("Name: "+localDevice.getFriendlyName());
        } catch (BluetoothStateException e) {
            System.err.println("Cannot get local device: " + e);
        }

        try {
            BT_Server server = new BT_Server();
            server.start();
        }
        catch(IOException e)
        {
            System.out.println(e);
        }

        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);

        response.getWriter().println("<h1>Hello Servlet</h1>");
        response.getWriter().println("session=" + request.getSession(true).getId());
    }
}