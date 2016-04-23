package reportserver;

import java.io.FileNotFoundException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.bluetooth.BluetoothStateException;
import javax.servlet.AsyncContext;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static java.lang.Thread.sleep;


public class WebServer extends CommonServer {

    private Server server;
    private String siteAddress;
    private static Map< WebActionType, AsyncContext > webActions = new HashMap< WebActionType, AsyncContext >();
    private CommonUserInterface ui;
    private static Logger log = Logger.getLogger(ReportServer.class);
    private Thread threadLocalHandler;

    WebServer(int port, String siteAddress_, CommonUserInterface messageForUser_) {
        setState(ServerState.SERVER_INITIALIZING);
        server = new Server(port);
        siteAddress = siteAddress_;
        ui = messageForUser_;
    }

    private URI getWebRootResourceUri() throws FileNotFoundException, URISyntaxException {
        URL indexUri = this.getClass().getClassLoader().getResource(siteAddress);
        if (indexUri == null) {
            FileNotFoundException e = new FileNotFoundException("Unable to find resource " + siteAddress);
            log.error(e);
            throw e;
        }
        return indexUri.toURI();
    }

    public void init() throws FileNotFoundException, URISyntaxException {
        setState(ServerState.SERVER_INITIALIZING);

        threadLocalHandler = new Thread(() -> {
                                while(true) {
                                    //Локальные обработчики
                                    userMessageHandler();
                                    try {
                                        sleep(500);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });

        System.setProperty("org.apache.jasper.compiler.disablejsr199", "false");

        //Для того чтобы jetty писала в лог в базу через log4j
        System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.Slf4jLog");

        WebAppContext context = new WebAppContext();

        context.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
                ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/.*taglibs.*\\.jar$");

        try {
            URI baseUri = getWebRootResourceUri();
            context.setResourceBase( baseUri.toASCIIString() );
        } catch (URISyntaxException e) {
            log.error(e);
            return;
        }

        context.setContextPath("/");
        context.setClassLoader( new URLClassLoader(new URL[0], this.getClass().getClassLoader() ) );
        context.setParentLoaderPriority(true);

        //Create JSP Servlet (must be named "jsp")
        ServletHolder holderJsp = new ServletHolder("jsp", JettyJspServlet.class);
        holderJsp.setInitOrder(0);
        holderJsp.setInitParameter("logVerbosityLevel", "WARN");
        holderJsp.setInitParameter("fork", "false");
        holderJsp.setInitParameter("xpoweredBy", "false");
        holderJsp.setInitParameter("compilerTargetVM", "1.7");
        holderJsp.setInitParameter("compilerSourceVM", "1.7");
        holderJsp.setInitParameter("keepgenerated", "true");
        context.addServlet(holderJsp, "*.jsp");

        // Add Application Servlets
        context.addServlet(new ServletHolder( new WebServer.ServletBtStatus()), "/btstatus");
        context.addServlet(new ServletHolder( new WebServer.ServletBtStart()), "/btstart");
        context.addServlet(new ServletHolder( new WebServer.ServletBtStop()), "/btstop");

        context.addServlet(new ServletHolder( new WebServer.ServletTableRefresh()), "/tablerefresh");
        context.addServlet(new ServletHolder( new WebServer.ServletUserMessageHandler()), "/usermessage");
        context.addServlet(new ServletHolder( new WebServer.ServletGetOldUserMessageHandler()), "/get-old-user-message");
        context.addServlet(new ServletHolder( new WebServer.ServletGetDetourFromDb()), "/get-detour-from-db");
        context.addServlet(new ServletHolder( new WebServer.ServletGetBluetoothMac()), "/get-bluetooth-mac");

        server.setHandler(context);

        threadLocalHandler.start();
        setState(ServerState.SERVER_STOPPED);
    }

    public void stop() throws Exception {
        try {
            server.stop();
        } catch(Exception e) {
            log.error(e);
            return;
        }

        setState(ServerState.SERVER_STOPPED);
    }

    public void start() throws Exception {
        try {
            server.start();
            setState(ServerState.SERVER_ACTIVE);
        } catch (Exception e) {
            log.error(e);

        }
        setState(ServerState.SERVER_ACTIVE);
    }

    static boolean isWebActionExist(WebActionType type) {
        return webActions.containsKey(type);
    }

    public static synchronized void putWebAction(WebActionType type, AsyncContext context) {
        webActions.put(type, context);
    }

    public static synchronized AsyncContext popWebAction(WebActionType type) throws NullPointerException {
        AsyncContext action = webActions.get(type);
        webActions.remove(type);
        return action;
    }

    public void userMessageHandler() {
        try {
            if (isWebActionExist(WebActionType.SEND_USER_MESSAGE)) {
                JSONObject text = ui.popUserMessage();
                AsyncContext asyncRequest = popWebAction(WebActionType.SEND_USER_MESSAGE);
                ServletResponse response = asyncRequest.getResponse();
                response.setContentType("text/html");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().print(text.toString());
                response.getWriter().flush();
                asyncRequest.complete();
            }
        } catch (NullPointerException | NoSuchElementException e) {

        } catch (IOException e) {
            log.warn(e);
        }
    }

    /**
     *  Пользовательские сервлеты
     *  @{
     */

    public class ServletBtStart extends HttpServlet
    {
        private Logger log = Logger.getLogger(reportserver.WebServer.ServletBtStart.class);

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            try {
                ReportServer.bluetoothServerStart();
            } catch (Exception e) {
                log.error(e);
            }

            ui.sendUserMessage("Запуск bluetooth-сервера");

            try {
                response.setContentType("text/html");
                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject responseStatus = new JSONObject();
                responseStatus.put("status", ReportServer.getStateBluetoothServer().toString());
                response.getWriter().println(responseStatus.toString());
            } catch (BluetoothStateException e) {
                log.error(e);
            }
        }
    }

    public class ServletBtStop extends HttpServlet
    {
        private Logger log = Logger.getLogger(reportserver.WebServer.ServletBtStop.class);

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            try {
                ReportServer.bluetoothServerStop();
            } catch (Exception e) {
                log.error(e);
            }
            ui.sendUserMessage("Остановка bluetooth-сервера");
            try {
                response.setContentType("text/html");
                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject responseStatus = new JSONObject();
                responseStatus.put("status", ReportServer.getStateBluetoothServer().toString());
                response.getWriter().println(responseStatus.toString());
            } catch (BluetoothStateException e) {
                log.error(e);
            }
        }
    }

    public class ServletBtStatus extends HttpServlet
    {
        private Logger log = Logger.getLogger(reportserver.WebServer.ServletBtStatus.class);
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            try {
                response.setContentType("text/html");
                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject responseStatus = new JSONObject();
                responseStatus.put("status", ReportServer.getStateBluetoothServer().toString());
                response.getWriter().println(responseStatus.toString());
            } catch (BluetoothStateException e) {
                log.error(e);
            }
        }
    }

    public class ServletTableRefresh extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            AsyncContext asyncContext = request.startAsync();
            asyncContext.setTimeout(0);

            WebServer.putWebAction(WebActionType.REFRESH_DETOUR_TABLE,
                    asyncContext);

        }
    }

    public class ServletUserMessageHandler extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            AsyncContext asyncContext = request.startAsync();
            asyncContext.setTimeout(0);

            WebServer.putWebAction(WebActionType.SEND_USER_MESSAGE, asyncContext);
        }
    }

    public class ServletGetOldUserMessageHandler extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

            try {
                response.setContentType("text/html");
                response.setStatus(HttpServletResponse.SC_OK);

                DatabaseDriver databaseDriver = ReportServer.getDatabaseDriver();
                String[] messages = databaseDriver.getUserMessages();
                String[] dates = databaseDriver.getUserMessagesDate();

                JSONArray fullresponse = new JSONArray();

                for(int i = 0; i < messages.length; ++i) {
                    if ((dates[i] != null) && (messages[i]!= null)) {
                        JSONObject responseJson = new JSONObject();
                        responseJson.put("date", dates[i]);
                        responseJson.put("text", messages[i]);

                        fullresponse.add(responseJson);
                    }
                }
                response.setCharacterEncoding("UTF-8");
                response.getWriter().println(fullresponse.toString());
            } catch (BluetoothStateException e) {
                log.error(e);
            }
        }
    }

    public class ServletGetDetourFromDb extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            try {
                response.setContentType("text/html");
                response.setStatus(HttpServletResponse.SC_OK);

                reportserver.DatabaseDriver databaseDriver = reportserver.ReportServer.getDatabaseDriver();

                JSONArray fullresponse = new JSONArray();

                try {
                    java.util.ArrayList<Integer> ids = databaseDriver.getLatest10IdsDetour();

                    for(Integer i : ids) {
                        boolean isFinishedRoute = databaseDriver.getStatusFromDetourTable(i);
                        if (isFinishedRoute) {
                            JSONObject responseJson = new JSONObject();
                            responseJson.put("user_name", databaseDriver.getUserNameFromDetourTable(i));
                            responseJson.put("route_name", databaseDriver.getRouteNameFromDetourTable(i));
                            responseJson.put("start_time", databaseDriver.getStartTimeFromDetourTable(i));
                            responseJson.put("end_time", databaseDriver.getEndTimeFromDetourTable(i));

                            fullresponse.add(responseJson);
                        }
                    }
                } catch (java.sql.SQLException e) {
                    JSONObject responseJson = new JSONObject();
                    responseJson.put("user_name", "SQL query error...");
                    responseJson.put("route_name", "SQL query error...");
                    responseJson.put("start_time", "SQL query error...");
                    responseJson.put("end_time", "SQL query error...");
                    fullresponse.add(responseJson);
                }

                response.setCharacterEncoding("UTF-8");
                response.getWriter().println(fullresponse.toString());
            } catch (BluetoothStateException e) {
                log.error(e);
            }
        }
    }

    public class ServletGetBluetoothMac extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);

            JSONObject responseJson = new JSONObject();
            responseJson.put("mac", ReportServer.getBluetoothMacAddress());

            response.setCharacterEncoding("UTF-8");
            response.getWriter().println(responseJson.toString());
        }
    }

    /**
    *   @}
    */
}



