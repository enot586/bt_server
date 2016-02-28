
import java.net.URL;
import java.net.URLClassLoader;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;


import java.io.IOException;


public class ReportServer {


    public static void main(String[] args) throws IOException, InterruptedException {

        System.setProperty("org.apache.jasper.compiler.disablejsr199", "false");

        String jetty_home = System.getProperty("jetty.home",".");

        Server server = new Server(8080);

        WebAppContext context = new WebAppContext();

        context.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
                             ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/.*taglibs.*\\.jar$");

        context.setResourceBase(jetty_home+"/src/webapp");
        context.setContextPath("/");

        //context.setAttribute("javax.servlet.context.tempdir",scratchDir);

        context.setClassLoader(new URLClassLoader(new URL[0], ReportServer.class.getClassLoader()));
        context.setParentLoaderPriority(true);

        // Add Default Servlet (must be named "default")
        ServletHolder holderDefault = new ServletHolder("default", DefaultServlet.class);
        holderDefault.setInitParameter("resourceBase", jetty_home+"/src/webapp");
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
        tabSynchronizeHolder.setInitParameter("resourceBase", jetty_home+"/src/webapp");
        tabSynchronizeHolder.setInitParameter("dirAllowed", "true");
        context.addServlet(tabSynchronizeHolder, "/TabSynchronize");

        ServletHolder exampleJspHolder = new ServletHolder();
        tabSynchronizeHolder.setInitParameter("resourceBase", jetty_home+"/src/webapp/WEB-INF/jsps");
        exampleJspHolder.setForcedPath(jetty_home+"/src/webapp/WEB-INF/jsps/example.jsp");
        //exampleJspHolder.setInitParameter("resourceBase", jetty_home+"/src/webapp/WEB-INF/jsps");
        //exampleJspHolder.setInitParameter("dirAllowed", "true");
        exampleJspHolder.setName("example.jsp");
        context.addServlet(exampleJspHolder, "/example");


        server.setHandler(context);

        try {
            server.start();
            server.join();
        }
        catch (Exception e)
        {
            System.out.println(e);
        }

    }



}



