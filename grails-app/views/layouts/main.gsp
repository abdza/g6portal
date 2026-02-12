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
  <!-- Favicons -->
  <asset:link rel="icon" type="image/x-ico" href="images/favicon.ico"/>
  <asset:link href="images/apple-touch-icon.png" rel="apple-touch-icon"/>

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

  <!-- ======= Header ======= -->
  <header id="header" class="header fixed-top d-flex align-items-center">

    <div class="d-flex align-items-center justify-content-between">
      <a href="index.html" class="logo d-flex align-items-center">
<g:if test="${grailsApplication.config.getProperty('info.app.logo_slug')}">
        <img src="<g:download_file slug='${grailsApplication.config.getProperty('info.app.logo_slug')}'/>" alt="">
</g:if>
<g:else>
  <g:if test="${grailsApplication.config.getProperty('info.app.logo_url')}">
          <img src="${grailsApplication.config.getProperty('info.app.logo_url')}" alt="">
  </g:if>
  <g:else>
          <img src="${resource(dir:'images',file:'logo.png')}" alt="">
  </g:else>
</g:else>
        <span class="d-none d-lg-block">
<g:if test="${grailsApplication.config.getProperty('info.app.name')}">
${grailsApplication.config.getProperty('info.app.name')}
</g:if>
<g:else>
G6 Portal
</g:else>
        </span>
      </a>
      <i class="bi bi-list toggle-sidebar-btn"></i>
    </div><!-- End Logo -->

    <div class="search-bar">
      <g:form class="search-form d-flex align-items-center" controller='portalSearch' action='index' method='get'>
        <input type="text" name="query" placeholder="Search" title="Enter search keyword">
        <button type="submit" title="Search"><i class="bi bi-search"></i></button>
      </g:form>
    </div><!-- End Search Bar -->

    <nav class="header-nav ms-auto">
      <ul class="d-flex align-items-center">

        <li class="nav-item d-block d-lg-none">
          <a class="nav-link nav-icon search-bar-toggle" href="#">
            <i class="bi bi-search"></i>
          </a>
        </li><!-- End Search Icon-->

        <li class="nav-item dropdown has-megamenu">

          <a class="nav-link nav-icon" href="#" data-bs-toggle="dropdown">
            <i class="bi bi-bank"></i>
          </a><!-- End Dept Icon -->

          <ul class="dropdown-menu dropdown-menu-end dropdown-menu-arrow megamenu">
            <div class='row g-3'>
            <g:tree_menu module='portal' name='main_menu'/>
            </div>
          </ul><!-- End main menu Dropdown Items -->

        </li><!-- End main menu Nav -->

        <li class="nav-item dropdown pe-3">

          <a class="nav-link nav-profile d-flex align-items-center pe-0" href="#" data-bs-toggle="dropdown">
	  <g:if test="${curuser}">
            <g:file_exists module='profile_pic' slug='${curuser?.profilepic?.slug}'>
            <img src="<g:download_file module='profile_pic' slug='${curuser?.profilepic?.slug}'/>" alt="Profile" class="rounded-circle">
            </g:file_exists>
            <g:file_not_exists module='profile_pic' slug='${curuser?.profilepic?.slug}'>
            <img src="${resource(dir:'images',file:'profile.png')}" alt="Profile" class="rounded-circle">
            </g:file_not_exists>
            <span class="d-none d-md-block dropdown-toggle ps-2">${curuser.name}</span>
	  </g:if>
	  <g:else>
            <img src="${resource(dir:'images',file:'profile.png')}" alt="Profile" class="rounded-circle">
            <span class="d-none d-md-block dropdown-toggle ps-2">Login</span>
	  </g:else>
          </a><!-- End Profile Iamge Icon -->

          <ul class="dropdown-menu dropdown-menu-end dropdown-menu-arrow profile">
	  <g:if test="${curuser}">
            <li class="dropdown-header">
              <h6>${curuser.name}</h6>
            <g:form name="roleselect" action="changerole" controller="user">
              <g:if test="${session?.rolestext?.size()>1}">
                <label for="chosenrole">Role &#58; </label>
                <select name="chosenrole" onchange="submit()">
                  <g:each in="${session?.rolestext}" status="i" var="role">
                    <g:if test="${session?.chosenrole && session?.chosenrole==i}">
                      <g:set var="chosen" value="selected"/>
                    </g:if>
                    <g:else>
                      <g:set var="chosen" value=""/>
                    </g:else>
                    <option value='${i}' ${chosen}>${role?.toString().size()>33?role?.toString()[0..12] + '...' + role?.toString()[-12..-1]:role}</option>
                  </g:each>
                </select>
              </g:if>
              <g:else>
                <g:if test="${curuser?.isAdmin}">
                  <label>Role &#58; Portal Admin</label>
                </g:if>
                <g:else>
                  <g:if test="${session?.rolestext[0]}">
                    <label>Role &#58; ${session?.rolestext[0]}</label>
                  </g:if>
                  <g:else>
                    <g:set var="isadmin" value="${g6portal.UserRole.findByUserAndRole(curuser,'Admin')}"/>
                    <g:if test="${isadmin}">
                      <label>Role &#58; Admin</label>
                    </g:if>
                    <g:else>
                      <label>Role &#58; None</label>
                    </g:else>
                  </g:else>
                </g:else>
              </g:else>
              <g:hiddenField name='frompage' value="${request?.getRequestURI()}"/>
            </g:form>
            </li>
            <li>
              <hr class="dropdown-divider">
            </li>

            <li>
              <g:link class="dropdown-item d-flex align-items-center" controller='user' action='my_profile'>
                <i class="bi bi-person"></i>
                <span>My Profile</span>
              </g:link>
            </li>
            <li>
              <hr class="dropdown-divider">
            </li>
            <li>
              <g:link class="dropdown-item d-flex align-items-center" controller='user' action='change_password'>
                <i class="bi bi-key"></i>
                <span>Change Password</span>
              </g:link>
            </li>
            <li>
              <hr class="dropdown-divider">
            </li>

            <li>
              <g:link class="dropdown-item d-flex align-items-center" controller='portalPage' action='display' params="[module:'portal',slug:'help']">
                <i class="bi bi-question-circle"></i>
                <span>Need Help?</span>
              </g:link>
            </li>
            <li>
              <hr class="dropdown-divider">
            </li>

            <g:if test="${session?.adminlink}">
            <li>
              <g:link controller="user" action="restoreadmin" class="dropdown-item d-flex align-items-center">
                <i class="bi bi-box-arrow-right"></i>
                <span>Restore Admin</span>
              </g:link>
            </li>
            <li>
              <hr class="dropdown-divider">
            </li>
            </g:if>

            <li>
              <g:link controller="user" action="logout" class="dropdown-item d-flex align-items-center">
                <i class="bi bi-box-arrow-right"></i>
                <span>Sign Out</span>
              </g:link>
            </li>
	  </g:if>
	  <g:else>
            <li class="dropdown-header">
                  <g:form controller="user" action="authenticate" class="row g-3 needs-validation">

                    <div class="col-12">
                      <div class="input-group has-validation">
                        <span class="input-group-text" id="inputGroupPrepend">@</span>
                        <g:textField placeholder='Username' name="username" class="form-control" id="username"/>
                        <div class="invalid-feedback">Please enter your username.</div>
                      </div>
                    </div>

                    <div class="col-12">
                      <g:passwordField placeholder='Password' name="password" class="form-control" id="password"/>
                      <div class="invalid-feedback">Please enter your password!</div>
                    </div>

                    <div class="col-12">
                      <button class="btn btn-primary w-100" type="submit">Login</button>
                    </div>
                    <g:if test="${grailsApplication.config.getProperty('server.allow_registration')=='true'}">
                    <div class="col-12">
                      <p class="small mb-0">Don't have account? <g:link controller='user' action='register'>Create an account</g:link></p>
                    </div>
                    </g:if>
                    <g:if test="${grailsApplication.config.getProperty('google.oauth.clientId')}">
                    <div class="col-12">
                        <g:link controller="googleOAuth" action="initiate" class="btn btn-primary w-100">
                            Sign in with Google
                        </g:link>
                    </div>
                    </g:if>
                  </g:form>
	    </li>
	  </g:else>

          </ul><!-- End Profile Dropdown Items -->
        </li><!-- End Profile Nav -->

      </ul>
    </nav><!-- End Icons Navigation -->

  </header><!-- End Header -->

  <!-- ======= Sidebar ======= -->
  <aside id="sidebar" class="sidebar">

    <ul class="sidebar-nav" id="sidebar-nav">

      <li class="nav-item">
        <g:link controller='portalPage' action='home' class="nav-link ">
          <i class="bi bi-grid"></i>
          <span>Dashboard</span>
        </g:link>
      </li><!-- End Dashboard Nav -->
      <g:side_menu module='${params.module}' slug='${params.slug}'/>

<g:if test="${curuser && (curuser?.isAdmin || curuser.developerlist()?.size()>0 || curuser.adminlist()?.size()>0)}">
<li class='nav-item'>
<a class='nav-link collapsed' data-bs-target='#admin-sidemenu-nav' data-bs-toggle='collapse' href='#'>
<i class='bi bi-tools'></i> <span>Admin</span>
<i class='bi bi-chevron-down ms-auto'></i></a><ul class='nav-content collapse' data-bs-parent='#sidebar-nav' id='admin-sidemenu-nav'>

        <li class="nav-item">
    <g:link controller='fileLink' action='index' class='nav-link collapsed'>
            <i class="bi bi-file-richtext"></i>
            <span>File</span>
    </g:link>
        </li><!-- End File Nav -->

        <li class="nav-item">
    <g:link controller='portalFileManager' action='index' class='nav-link collapsed'>
            <i class="bi bi-folder"></i>
            <span>File Manager</span>
    </g:link>
        </li><!-- End File Nav -->

        <li class="nav-item">
    <g:link controller='portalTrackerData' action='index' class='nav-link collapsed'>
            <i class="bi bi-upload"></i>
            <span>Data Update</span>
    </g:link>
        </li><!-- End File Nav -->

      <li class="nav-item">
	<g:link controller='userRole' action='index' class='nav-link collapsed'>
          <i class="bi bi-person-bounding-box"></i>
          <span>Role</span>
	</g:link>
      </li><!-- End Role Nav -->

        <li class="nav-item">
    <g:link controller='portalSetting' action='index' class='nav-link collapsed'>
            <i class="bi bi-sliders"></i>
            <span>Settings</span>
    </g:link>
        </li><!-- End Settings Nav -->

        <li class="nav-item">
    <g:link controller='portalModule' action='index' class='nav-link collapsed'>
            <i class="bi bi-stack-overflow"></i>
            <span>Module</span>
    </g:link>
        </li><!-- End Module Nav -->

    <g:if test="${session['developermodules']}">
      <li class="nav-item">
    <g:link controller='portalPage' action='index' class='nav-link collapsed'>
            <i class="bi bi-window-sidebar"></i>
            <span>Page</span>
    </g:link>
        </li><!-- End Page Nav -->

        <li class="nav-item">
    <g:link controller='portalTracker' action='index' class='nav-link collapsed'>
            <i class="bi bi-ui-checks"></i>
            <span>Tracker</span>
    </g:link>
        </li><!-- End Tracker Nav -->
    </g:if>

        <li class="nav-item">
    <g:link controller='portalTree' action='index' class='nav-link collapsed'>
            <i class="bi bi-diagram-3"></i>
            <span>Tree</span>
    </g:link>
      </li><!-- End Tree Nav -->

        <li class="nav-item">
    <g:link controller='portalScheduler' action='index' class='nav-link collapsed'>
            <i class="bi bi-clock"></i>
            <span>Scheduler</span>
    </g:link>
      </li><!-- End Tree Nav -->

        <li class="nav-item">
    <g:link controller='portalEmail' action='index' class='nav-link collapsed'>
            <i class="bi bi-mailbox"></i>
            <span>E-mail</span>
    </g:link>
      </li><!-- End Tree Nav -->

      <li class="nav-item">
	<g:link controller='user' action='index' class='nav-link collapsed'>
          <i class="bi bi-person"></i>
          <span>User</span>
	</g:link>
      </li><!-- End User Nav -->

      <li class="nav-item">
	<g:link controller='portalErrorLog' action='index' class='nav-link collapsed'>
          <i class="bi bi-clipboard-x"></i>
          <span>Error</span>
	</g:link>
      </li><!-- End Error Nav -->

</ul></li>
</g:if><!-- End Admin sidebar -->

    </ul>

  </aside><!-- End Sidebar-->

  <%
    def pageslug = ''
    if(params.module) { pageslug += params.module }
    if(params.slug) { pageslug += '_' + params.slug }
  %>
  <main id="main" class="main ${pageslug}">

    <section class="section">
      <div class="row">
<g:layoutBody/>
      </div>
    </section>

  </main><!-- End #main -->

  <!-- ======= Footer ======= -->
  <footer id="footer" class="footer">
    <div class="copyright">
      &copy; Copyright <strong><span>
<g:if test="${grailsApplication.config.getProperty('info.app.copyright')}">
${grailsApplication.config.getProperty('info.app.copyright')}
</g:if>
<g:else>
G6 Portal
</g:else>
      </span></strong>. All Rights Reserved
    </div>
    <div class="credits">
      Designed by <a href="
<g:if test="${grailsApplication.config.getProperty('info.app.homepage')}">
${grailsApplication.config.getProperty('info.app.homepage')}
</g:if>
<g:else>
https://g6portal.abdullahsolutions.com/
</g:else>
      ">
<g:if test="${grailsApplication.config.getProperty('info.app.team')}">
${grailsApplication.config.getProperty('info.app.team')}
</g:if>
<g:else>
G6Portal Portal Team
</g:else>
      </a>
    </div>
  </footer><!-- End Footer -->

  <a href="#" class="back-to-top d-flex align-items-center justify-content-center"><i class="bi bi-arrow-up-short"></i></a>

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



