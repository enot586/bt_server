package reportserver;

import org.apache.log4j.Logger;

import javax.bluetooth.BluetoothStateException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


class ServletBtStatus extends HttpServlet
{
    private static Logger log = Logger.getLogger(ServletBtStatus.class);
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        try {
            response.setContentType("text");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(ReportServer.getStateBluetoothServer().toString());
        } catch (BluetoothStateException e) {
            log.error(e);
        }
    }
}