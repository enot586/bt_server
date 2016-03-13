package reportserver;

import org.apache.log4j.Logger;
import reportserver.ReportServer;

import javax.bluetooth.BluetoothStateException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ServletBtStop extends HttpServlet
{
    private static Logger log = Logger.getLogger(ServletBtStop.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        try {
            ReportServer.bluetoothServerStop();
        } catch (Exception e) {
            log.error(e);
        }

        try {
            response.setContentType("text");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(ReportServer.bluetoothServerGetState().toString());
        } catch (BluetoothStateException e) {
            log.error(e);
        }
    }
}
