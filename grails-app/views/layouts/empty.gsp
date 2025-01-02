<!DOCTYPE html>
<html lang="en">

<head>
  <meta charset="utf-8">
  <meta content="width=device-width, initial-scale=1.0" name="viewport">

  <title>
<g:if test="${grailsApplication.config.getProperty('info.app.name')}">
${grailsApplication.config.getProperty('info.app.name')}
</g:if>
<g:else>
G6 Portal
</g:else>
<g:layoutTitle default="Grails"/>
</title>
  <meta content="" name="description">
  <meta content="" name="keywords">
  <asset:stylesheet src="application.css"/>
  <!-- Favicons -->
  <asset:link rel="icon" type="image/x-ico" href="assets/images/favicon.png"/>
  <asset:link href="assets/images/apple-touch-icon.png" rel="apple-touch-icon"/>

  <!-- Google Fonts -->
  <asset:stylesheet src="fonts.css"/>

  <!-- Vendor CSS Files -->
  <asset:stylesheet src="application.css"/>

    <g:layoutHead/>
</head>

<body>
<g:set var="curuser" value="${g6portal.User.get(session?.userid)}"/>
    <g:if test="${flash.message}">
      <asset:script>
        alert("${flash.message}");
      </asset:script>
    </g:if>
<g:layoutBody/>
  <!-- Vendor JS Files -->
  <asset:javascript src="jquery/jquery.min.js"></asset:javascript>
  <asset:javascript src="tagify/tagify.min.js"></asset:javascript>
  <asset:javascript src="tagify/tagify.polyfills.min.js"></asset:javascript>
  <asset:javascript src="dragsort/dragsort.js"></asset:javascript>
  <asset:javascript src="select2/js/select2.min.js"></asset:javascript>
  <asset:javascript src="bootstrap-5.3.3/js/bootstrap.bundle.min.js"></asset:javascript>
  <asset:javascript src="simple-datatables/simple-datatables.js"></asset:javascript>

  <!-- JS Charts -->
  <asset:javascript src="chart.js/chart.min.js"></asset:javascript>
  <asset:javascript src="echarts/echarts.min.js"></asset:javascript>
  <asset:javascript src="apexcharts/apexcharts.min.js"></asset:javascript>

  <!-- JS Editor -->
  <asset:javascript src="quill/quill.min.js"></asset:javascript>
  <asset:javascript src="tinymce/tinymce.min.js"></asset:javascript>

  <!-- Template Main JS File -->
  <asset:javascript src="niceadmin/js/main.js"></asset:javascript>
  <asset:javascript src="htmx.min.js"></asset:javascript>
  <asset:javascript src="hyperscript.min.js"></asset:javascript>
<asset:deferredScripts/>
</body>

</html>



