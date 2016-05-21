package reportserver;


import javax.servlet.*;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;


public class TestStubWebServer extends WebServer {

    TestStubWebServer(int port, String siteAddress_, ActionFromWeb webActionsHandler_, CommonUserInterface messageForUser_) {
        super(port, siteAddress_, webActionsHandler_, messageForUser_);
    }

    @Override
    public synchronized AsyncContext popWebAction(WebActionType type) throws NullPointerException {
        return new AsyncContext() {
            @Override
            public ServletRequest getRequest() {
                return null;
            }

            @Override
            public ServletResponse getResponse() {
                return null;
            }

            @Override
            public boolean hasOriginalRequestAndResponse() {
                return false;
            }

            @Override
            public void dispatch() {

            }

            @Override
            public void dispatch(String s) {

            }

            @Override
            public void dispatch(ServletContext servletContext, String s) {

            }

            @Override
            public void complete() {

            }

            @Override
            public void start(Runnable runnable) {

            }

            @Override
            public void addListener(AsyncListener asyncListener) {

            }

            @Override
            public void addListener(AsyncListener asyncListener, ServletRequest servletRequest, ServletResponse servletResponse) {

            }

            @Override
            public <T extends AsyncListener> T createListener(Class<T> aClass) throws ServletException {
                return null;
            }

            @Override
            public void setTimeout(long l) {

            }

            @Override
            public long getTimeout() {
                return 0;
            }
        };
    }

    @Override
    public synchronized void putWebAction(WebActionType type, AsyncContext context) {
    }

    @Override
    boolean isWebActionExist(WebActionType type) {
        return true;
    }

    @Override
    public void userMessageHandler() {
    }

    @Override
    public void init() throws FileNotFoundException, URISyntaxException {
          setState(ServerState.SERVER_STOPPED);
    }

    public void stop() throws Exception {
        setState(ServerState.SERVER_STOPPED);
    }

    public void start() throws Exception {
        setState(ServerState.SERVER_ACTIVE);
    }
}
