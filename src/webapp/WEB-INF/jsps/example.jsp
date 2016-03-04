бю<%@ page language="java" contentType="text/html; charset=ISO-8859-1" pageEncoding="ISO-8859-1" isELIgnored="false" %>
<html>
<head>
<title>Sample JSP Page</title>

</head>
<body onLoad="getCustomerInfo()">
    <%= request.getParameter("server") %>
    Current date is: <%=new java.util.Date()%>
</body>
</html>
