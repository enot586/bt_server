
import java.io.IOException;
import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.LocalDevice;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class BTResponseServlet extends HttpServlet
{
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        request.getRequestDispatcher("/example.jsp").forward(request,response);
    }
}