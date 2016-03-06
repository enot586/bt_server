package reportserver;

import javax.bluetooth.BluetoothStateException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


public class ServletBtStatus extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        try {
            response.setContentType("text");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(ReportServer.bluetoothServerGetState().toString());
        } catch (BluetoothStateException e) {
            System.err.println("Cannot get bluetooth server status: " + e);
        }
    }
}