package reportserver;

import javax.bluetooth.LocalDevice;
import java.io.*;
import org.apache.log4j.Logger;
import static java.lang.Thread.sleep;

public class ReportServer {

    private static final int versionMajor = 1;
    private static final int versionMinor = 0;
    private static final int versionBuild = 13;

    private static WebServer webServer;
    private static BluetoothServer bluetoothServer;
    private static DatabaseDriver databaseDriver;
    private static FeedbackUsersMessage userFeedback;
    private static WebActionsHandler userWebActionsHandler;
    private static BluetoothTransactionHandler bluetoothTransactionHandler;

    private static final Logger log = Logger.getLogger(ReportServer.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        log.info("Application status:\t\t[INIT]");

        String logMessage = "Application database driver\t\t";
        try {
            databaseDriver = new DatabaseDriver();
            databaseDriver.init(ProjectDirectories.commonDatabaseRelativePath,
                                ProjectDirectories.localDatabaseRelativePath);

            LocalDevice ld = LocalDevice.getLocalDevice();
            databaseDriver.initDatabaseVersion(ld.getBluetoothAddress());

            userFeedback = new FeedbackUsersMessage(databaseDriver);
            log.info(logMessage+"[OK]");
        } catch(Exception e) {
            log.info(logMessage+"[FAIL]");
            log.error(e);
            return;
        }

        logMessage = "Bluetooth driver init\t\t";
        try {
            bluetoothServer = new BluetoothServer(userFeedback);
            bluetoothServer.init();
            log.info(logMessage + "[OK]");
        } catch(Exception e) {
            log.info(logMessage+"[FAIL]");
            log.error(e);
            return;
        }

        logMessage = "Web server init\t\t";
        try {
            userWebActionsHandler = new WebActionsHandler(bluetoothServer, databaseDriver);

            try {
                int port = Integer.parseInt(args[0]);
                webServer = new WebServer(port, "webapp", userWebActionsHandler, userFeedback);
            } catch(Exception e) {
                System.out.println("Error: incorrect port number.");
                log.error(e);
                printConsoleHelp();
                return;
            }

            webServer.init();
            log.info(logMessage+"[OK]");

            logMessage = "Web server start\t\t";
            webServer.start();
            log.info(logMessage+"[OK]");
        } catch(Exception e) {
            log.info(logMessage+"[FAIL]");
            log.error(e);
            return;
        }

        bluetoothTransactionHandler = new BluetoothTransactionHandler(bluetoothServer, webServer,
                                                                        databaseDriver, userFeedback);

        //Bluetooth-сервер запускается при старте
        try {
            bluetoothServer.start();
            log.info("Bluetooth status:\t\t[RUNNING]");
        } catch (Exception e) {
            log.error(e);
            userFeedback.sendUserMessage("Ошибка: не удалось запустить bluetooth.");
            log.info("Bluetooth status:\t\t[STOPED]");
        }

        log.info("Application status:\t\t[RUNNING]");

        while (true) {
            //В основном потоке обрабатываем принятые пакеты
            bluetoothTransactionHandler.run();
            sleep(500);
        }
    }

    private static void printConsoleHelp()  {
        //String ver = ReportServer.class.getPackage().getImplementationVersion();
        System.out.println("reportserver v"+versionMajor+"."+versionMinor+"build"+versionBuild+"\n"+
                "Copyright (C) 2016 M&D, Inc.");
        //usage format:
        //Usage: reportserver [-aDde] [-f | -g] [-n number] [-b b_arg | -c c_arg] req1 req2 [opt1 [opt2]]
        System.out.println("Usage: reportserver port");
        System.out.println("\tport: web-server port number");
    }


}
