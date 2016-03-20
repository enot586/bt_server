package reportserver;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

class ServletTableRefresh extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(0);

        ReportServer.putWebAction(WebActionType.WEB_ACTION_REFRESH_DETOUR_TABLE,
                                    asyncContext);

    }
}
