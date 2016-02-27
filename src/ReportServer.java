
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

import javax.bluetooth.*;
import com.intel.bluetooth.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.annotations.ServletContainerInitializersStarter;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;


import org.eclipse.jetty.util.IO;

import java.io.IOException;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;


public class ReportServer {


    public static void main(String[] args) throws IOException, InterruptedException {

        System.setProperty("org.apache.jasper.compiler.disablejsr199", "false");

        Server server = new Server(8080);

        WebAppContext context = new WebAppContext();

        context.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
                             ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/.*taglibs.*\\.jar$");

        context.setResourceBase("/webapp");
        context.setContextPath("/");

//        ContainerInitializer initializer = new ContainerInitializer();
//        context.setAttribute("org.eclipse.jetty.containerInitializers", initializer);
//
//        context.addBean(new ServletContainerInitializersStarter(context), true);

        context.setClassLoader(new URLClassLoader(new URL[0], ReportServer.class.getClassLoader()));
        context.setParentLoaderPriority(true);

        // Add Default Servlet (must be named "default")
        ServletHolder holderDefault = new ServletHolder("default", DefaultServlet.class);
        holderDefault.setInitParameter("resourceBase", "/webapp/WEB-INF/html");
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
        context.addServlet(BTResponseServlet.class, "/example");

//        ServletHolder exampleJspHolder = new ServletHolder();
//        exampleJspHolder.setInitParameter("resourceBase", "/webapp/WEB-INF/html");
//        exampleJspHolder.setInitParameter("dirAllowed", "true");
//        exampleJspHolder.setName("example.jsp");
//        context.addServlet(exampleJspHolder, "/example");


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



