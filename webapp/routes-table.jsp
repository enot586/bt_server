<%@ page language="java" contentType="text/html; charset=ISO-8859-1" pageEncoding="ISO-8859-1" isELIgnored="false" %>
<html>
  <head>
    <meta charset="utf-8">
    <meta name="description" content="">
    <meta name="keywords" content="">
    <title>Report server</title>
    <link rel="stylesheet" type="text/css" href="style.css">
  </head>
  
  <body>
    <center>
    <table width="90%" style="border-width:1px; border-color:#ffffff; border-style:solid; background-color:#ffffff;">
      <tr class="title-user-row">
        <td>id</td> <td>User</td> <td>Route</td> <td>Date</td>
      </tr>
      <%
        try {
          reportserver.ReportDatabaseDriver databaseDriver = reportserver.ReportServer.getDatabaseDriver();

          java.util.ArrayList<Integer> ids = databaseDriver.getRoutesTableIds();

          for(Integer i : ids) {
            out.println("<tr class=\"user-row\"><td>"+i+
                        "</td> <td>"+databaseDriver.getRoutesTableUser(i)+
                        "</td><td>"+databaseDriver.getRoutesTablePathRoutePicture(i)+
                        "</td> <td>"+databaseDriver.getRoutesTableDate(i)+
                        "</td></tr>");
          }
        } catch (java.sql.SQLException e) {

        }

      %>
    </table>
    </center>
  </body>

</html>
