<!--
  To change this template, choose Tools | Templates
  and open the template in the editor.
-->

<%@ page contentType="text/html;charset=UTF-8" %>

<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="layout" content="main" />
    <title>Uploaded Data</title>
  </head>
  <body>
    <h1>Uploaded Data</h1>
    <g:link action="show" id="${update.id}">Update ${update.id}</g:link>
    <g:if test="${warnings}">
      <g:each in="${warnings}" var="warning">
      <li>
        ${warning}
      </li>
      </g:each>
    </g:if>
    <g:if test="${results}">
      ${results}
    </g:if>
    <g:link action="show" id="${update.id}">Update ${update.id}</g:link>
  </body>
</html>
