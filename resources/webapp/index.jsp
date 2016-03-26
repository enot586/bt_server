<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="false" %>
<html>
  <head>
    <meta charset="utf-8">
    <meta name="description" content="">
    <meta name="keywords" content="">
    <title>Report server</title>
    <script src="jquery-1.12.1.js"></script>
    <script language="javascript" type="text/javascript">
    var request = false;
    try {
        request = new XMLHttpRequest();
    } catch (trymicrosoft) {
        try {
            request = new ActiveXObject("Msxml2.XMLHTTP");
        } catch (othermicrosoft) {
            try {
                request = new ActiveXObject("Microsoft.XMLHTTP");
            } catch (failed) {
                request = false;
            }
        }
    }

    if (!request)
     alert("Error initializing XMLHttpRequest!");

    function bluetoothServerStart() {
        var url = "/btstart";
        request.open("GET", url, true);
        request.onreadystatechange = updateBluetoothServerStatus;
        request.send(null);
    }

    function bluetoothServerStop() {
        var url = "/btstop";
        request.open("GET", url, true);
        request.onreadystatechange = updateBluetoothServerStatus;
        request.send(null);
    }

    function getBluetoothServerStatus() {
        var url = "/btstatus";
        request.open("GET", url, true);
        request.onreadystatechange = updateBluetoothServerStatus;
        request.send(null);
    }

    function updateBluetoothServerStatus() {
        var response = request.responseText;
        if (request.readyState == 4) {
            if (request.status == 200) {
                document.getElementById("btServerStatus").innerHTML = response;
            } else {
                //alert("status is " + request.status);
            }
        }
    }

    var userMessageNumber = 0;

    // Сообщения для пользователя
    function addUserMessageRow(text) {
      var currentDate = new Date();
      $('#userMessageTable').prepend("<tr><td><div class=\"debug-message\">"+currentDate.toLocaleString()+"</div></td>"+
                                     "<td><div class=\"debug-message\">"+text+"</div></td></tr>");

      ++userMessageNumber;

      if (userMessageNumber > 5) {
        $('#userMessageTable').children().find('tr').last().remove();
      }
    }

    function userMessageHandler() {
      $.ajax({
        url: '/usermessage',
        type: 'get',
        dataType: "text",
        success: function(data)
        {
          addUserMessageRow(data);
          userMessageHandler();
        }
      });
    }

    function onLoadRuner() {
      getBluetoothServerStatus();
      userMessageHandler();
    }

  </script>
  
  <link rel="stylesheet" type="text/css" href="style.css">
  <link rel="shortcut icon" href="img/favicon.ico" />

  </head>

  <body onload="onLoadRuner()" bgcolor="#ffffff">
   	<H1 class="main-title">Report server</H1>

    <table width="100%" height="100%" cellspacing="0" cellspacing="0" border="0">
      <tr>
        <td width="20%" valign="top" align="center">
        
          <img src="img/title.png" width="100%">
          <table width="100%" class="left-menu" cellspacing="0" cellspacing="0">
            <tr>
              <td>
                <table width="100%" cellspacing="0" cellspacing="0">
                  <tr>
                    <td align="left" colspan="2"><div name="bluetooth-address" class="bluetooth-mac" id="btMacAddress">Bluetooth mac: <%= reportserver.ReportServer.getBluetoothMacAddress() %></div></td>
                  </tr>
                  <tr>
                    <td align="left" width="50%"><div class="bluetooth-mac">Состояние процесса синхронизации:</div></td>
                    <td align="left" bgcolor="#cccccc"><div name="text1" class="btserver-status" id="btServerStatus">Чтение состояния...</div></td>
                  </tr>
                  <tr>
                    <td align="left"><a href="without-javascript.html" onClick="bluetoothServerStart(); return false">Запустить</a></td>
                    <td align="left"><a href="without-javascript.html" onClick="bluetoothServerStop(); return false">Остановить</a></td>
                  </tr>
                </table>
              </td>
            </tr>
            <tr>
              <td>
                <div name="bluetooth-address" class="bluetooth-mac">Журнал событий:</div>
                <table width="100%" cellspacing="0" cellspacing="0" id="userMessageTable">
                  <%
                  try {
                    reportserver.ReportDatabaseDriver databaseDriver = reportserver.ReportServer.getDatabaseDriver();

                    String[] messages = databaseDriver.getUserMessages();
                    String[] dates = databaseDriver.getUserMessagesDate();

                    for(int i = 0; i < 15; ++i) {
                      out.println("<tr class=\"debug-message\">"+
                                    "<td>"+dates[i]+"</td>"+
                                    "<td>"+messages[i]+"</td>"+
                                  "</tr>");
                    }
                  } catch (java.sql.SQLException e) {
                    out.println("<tr class=\"debug-message\"><td colspan=\"2\"><span class=\"error\">SQL query error...</span></td></tr>");
                  }
                  %>
                </table>
              </td>
            </tr>
          </table>
        </td> 
    
        <td valign="top">
          <iframe width="100%" height="100%" src="routes-table.jsp"></iframe>
        </td>
        
        <td width="10%"></td>
      </tr>
    </table>

  </body>
</html>