import java.io.File;
import java.io.FileNotFoundException;

import java.net.URI;
import java.net.URISyntaxException;

import java.net.URL;
import java.net.URLClassLoader;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;


import java.io.IOException;


public class ReportServer {


    private URI getWebRootResourceUri() throws FileNotFoundException, URISyntaxException {
        URL indexUri = ReportServer.class.getResource("/webapp");
        if (indexUri == null) {
            throw new FileNotFoundException("Unable to find resource " + "/webapp");
        }
        return indexUri.toURI();
    }

    public void Init() throws FileNotFoundException, URISyntaxException {
        System.setProperty("org.apache.jasper.compiler.disablejsr199", "false");

        //String jetty_home = System.getProperty("jetty.home","D:/projects/ReportServer");


        Server server = new Server(8080);

        WebAppContext context = new WebAppContext();

        context.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
                ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/.*taglibs.*\\.jar$");

        try {
            URI baseUri = getWebRootResourceUri();
            context.setResourceBase(baseUri.toASCIIString());
        }
        catch (URISyntaxException e)
        {

        }

        context.setContextPath("/");

        context.setClassLoader( new URLClassLoader(new URL[0], ReportServer.class.getClassLoader() ) );
        context.setParentLoaderPriority(true);

        // Add Default Servlet (must be named "default")
        ServletHolder holderDefault = new ServletHolder("default", DefaultServlet.class);

        try {
            URI baseUri = getWebRootResourceUri();
            holderDefault.setInitParameter("resourceBase", baseUri.toASCIIString());
        }
        catch  (URISyntaxException e)
        {

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
        ServletHolder tabSynchronizeHolder = new ServletHolder("TabSynchronize", TabSynchronize.class);
        try {
            URI baseUri = getWebRootResourceUri();
            tabSynchronizeHolder.setInitParameter( "resourceBase", baseUri.toASCIIString() );
        }
        catch  (URISyntaxException e) {

        }

        tabSynchronizeHolder.setInitParameter("dirAllowed", "true");
        context.addServlet(tabSynchronizeHolder, "/TabSynchronize");

        ServletHolder exampleJspHolder = new ServletHolder();
        exampleJspHolder.setForcedPath("/WEB-INF/jsps/example.jsp");
        exampleJspHolder.setName("example.jsp");
        context.addServlet(exampleJspHolder, "/example");

        server.setHandler(context);

        try {
            server.start();
            server.join();
        }
        catch (Exception e) {
            System.out.println(e);
        }
    }



    public static void main(String[] args) throws IOException, InterruptedException {

        ReportServer server = new ReportServer();

        try {
            server.Init();
        }
        catch(Exception e) {

        }
    }



}



