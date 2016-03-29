package reportserver;

import java.io.FileNotFoundException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import java.net.URL;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.json.simple.JSONObject;
import reportserver.CommonServer;

import javax.servlet.AsyncContext;
import javax.servlet.ServletResponse;


class WebServer extends CommonServer {

    private Server server;
    private String siteAddress;
    private static Map< WebActionType, AsyncContext > webActions = new HashMap< WebActionType, AsyncContext >();
    private static final LinkedList<JSONObject> userMessages = new LinkedList<JSONObject>();
    private static Logger log = Logger.getLogger(ReportServer.class);
    private Thread threadLocalHandler;

    WebServer(int port, String siteAddress_) {
        setState(ServerState.SERVER_INITIALIZING);
        server = new Server(port);
        siteAddress = siteAddress_;
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

        // Add Default Servlet (must be named "default")
        ServletHolder holderDefault = new ServletHolder("default", ServletForwarder.class);

        try {
            URI baseUri = getWebRootResourceUri();
            holderDefault.setInitParameter("resourceBase", baseUri.toASCIIString());
        } catch  (URISyntaxException e) {
            log.error(e);
            return;
        }

        holderDefault.setInitParameter("dirAllowed", "true");

        context.addServlet(holderDefault, "/");

        //Create JSP Servlet (must be named "jsp")
        ServletHolder holderJsp = new ServletHolder("jsp", JettyJspServlet.class);
        holderJsp.setInitOrder(0);
        holderJsp.setInitParameter("logVerbosityLevel", "DEBUG");
        holderJsp.setInitParameter("fork", "false");
        holderJsp.setInitParameter("xpoweredBy", "false");
        holderJsp.setInitParameter("compilerTargetVM", "1.7");
        holderJsp.setInitParameter("compilerSourceVM", "1.7");
        holderJsp.setInitParameter("keepgenerated", "true");
        context.addServlet(holderJsp, "*.jsp");

        // Add Application Servlets
        context.addServlet(ServletForwarder.class, "/date");
        context.addServlet(ServletBtStatus.class, "/btstatus");
        context.addServlet(ServletBtStart.class, "/btstart");
        context.addServlet(ServletBtStop.class, "/btstop");

        context.addServlet(ServletTableRefresh.class, "/tablerefresh");
        context.addServlet(ServletUserMessageHandler.class, "/usermessage");

        ServletHolder exampleJspHolder = new ServletHolder();
        exampleJspHolder.setForcedPath("/example.jsp");
        exampleJspHolder.setName("example.jsp");
        context.addServlet(exampleJspHolder, "/example");

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

    public static void sendUserMessage(Date date, String text) {
        JSONObject userMessage = new JSONObject();
        String pattern = "yyyy-MM-dd HH:mm:ss.SSS";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        String strDate = simpleDateFormat.format(date);
        userMessage.put("date", strDate);
        userMessage.put("text", text);
        synchronized (userMessages) {
            userMessages.add(userMessage);
        }
    }

    public static synchronized JSONObject popUserMessage() throws NoSuchElementException {
        JSONObject text = userMessages.peek();
        if (text == null)  throw new NoSuchElementException();
        userMessages.remove();
        return text;
    }

    public void userMessageHandler() {
        try {
            if (isWebActionExist(WebActionType.SEND_USER_MESSAGE)) {
                JSONObject text = popUserMessage();
                AsyncContext asyncRequest = popWebAction(WebActionType.SEND_USER_MESSAGE);
                ServletResponse response = asyncRequest.getResponse();
                response.setCharacterEncoding("UTF-8");
                response.getWriter().print(new String(text.toJSONString().getBytes("UTF-8")));
                response.getWriter().flush();
                asyncRequest.complete();
            }
        } catch (NullPointerException | NoSuchElementException e) {

        } catch (IOException e) {
            log.warn(e);
        }
    }

}



