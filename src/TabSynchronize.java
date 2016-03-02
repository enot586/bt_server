
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

            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println("<html><title>Hello Servlet</title><body>");
            response.getWriter().println("<p>");
            response.getWriter().println("Bluetooth module power: "+localDevice.isPowerOn()+"(may be wrong)<br>");
            response.getWriter().println("Address: "+localDevice.getBluetoothAddress()+"<br>");
            response.getWriter().println("Name: "+localDevice.getFriendlyName()+"<br>");
            response.getWriter().println("</body></html>");
        } catch (BluetoothStateException e) {
            System.err.println("Cannot get local device: " + e);
        }
    }
}