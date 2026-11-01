<%--
  index.jsp: the app's home page, which just sends the browser to the requisitions list.

  Tomcat serves index.jsp when someone opens the app's root URL: "/" on Railway, where
  the WAR is deployed as ROOT.war, or http://localhost:8081/mini-procurement/ locally.
  Without it, the root URL would be a 404.

  sendRedirect replies "302 Found" with a Location header, and the browser follows it.
  getContextPath() is "" on Railway and "/mini-procurement" locally, so this works in both.
--%>
<% response.sendRedirect(request.getContextPath() + "/requisitions"); %>
