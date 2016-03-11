<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="false" %>
<html>
  <head>
    <meta charset="utf-8">
    <meta name="description" content="">
    <meta name="keywords" content="">
    <title>Report server</title>
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




  </script>
  
  <link rel="stylesheet" type="text/css" href="style.css">
  <link rel="shortcut icon" href="img/favicon.ico" />

  </head>

  <body onload="getBluetoothServerStatus()" bgcolor="#ffffff">
  	
   	<H1>Report server</H1>

    <table width="100%" height="100%" cellspacing="0" cellspacing="0" border="0">
      <tr>
        <td width="20%" valign="top" align="center">
        
          <img src="img/title.png" width="100%">
          <table width="100%" class="left-menu" cellspacing="0" cellspacing="0">
            <tr>
              <td>
                <table width="100%" cellspacing="0" cellspacing="0">
                  <tr>
                    <td align="left" width="50%">Состояние процесса синхронизации:</td>
                    <td align="left" bgcolor="#cccccc"><div name="text1" class="btserver-status" id="btServerStatus">Чтение состояния...</div></td>
                  </tr>
                  <tr>
                    <td align="left"><a href="without-javascript.html" onClick="bluetoothServerStart(); return false">Запустить</a></td>
                    <td align="left"><a href="without-javascript.html" onClick="bluetoothServerStop(); return false">Остановить</a></td>
                  </tr>
                </table>

                <BR>
              </td>
            </tr>
            <!--
            <tr valign="top">
              <td><p><a href="/date">Example jsp</a></p></td>
            </tr>
            <tr valign="top">
              <td><p><a href="BluetoothSynchronization.html">Tab synchronize</a></p></td>
            </tr>
            <tr valign="top">
              <td><p><a href="/servlet0">Servlet 0</a></p></td>
            </tr>
            <tr valign="top">
              <td><p><a href="/servlet1">Servlet 1</a></p></td>
            </tr>
            <tr valign="top">
              <td><p><a href="/servlet2">Servlet 2</a></p></td>
            </tr>
            -->
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