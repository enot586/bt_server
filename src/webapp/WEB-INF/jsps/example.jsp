<%@ page language="java" contentType="text/html; charset=ISO-8859-1" pageEncoding="ISO-8859-1" isELIgnored="false" %>
<html>
<head>
<title>Java Code Geeks Snippets - Sample JSP Page</title>
<meta>
</meta>
</head>
<body>
    <%= request.getParameter("server") %>
    Current date is: <%=new java.util.Date()%>

</body>
</html>
