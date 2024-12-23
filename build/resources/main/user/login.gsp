<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="empty" />
        <g:set var="entityName" value="${message(code: 'user.label', default: 'User')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
      <section class="section register min-vh-100 d-flex flex-column align-items-center justify-content-center py-4">
        <div class="container">
          <div class="row justify-content-center">
            <div class="col-lg-4 col-md-6 d-flex flex-column align-items-center justify-content-center">

              <div class="d-flex justify-content-center py-4">
                <a href="index.html" class="logo d-flex align-items-center w-auto">
                  <img src="assets/img/logo.png" alt="">
                  <span class="d-none d-lg-block">G6 Portal</span>
                </a>
              </div><!-- End Logo -->

              <div class="card mb-3">

                <div class="card-body">

                  <div class="pt-4 pb-2">
                    <h5 class="card-title text-center pb-0 fs-4">Login to Your Account</h5>
                    <p class="text-center small">Enter your username & password to login</p>
                  </div>

                  <g:form useToken="true" controller="user" action="authenticate" class="row g-3 needs-validation">

                    <div class="col-12">
                      <label for="yourUsername" class="form-label">Username</label>
                      <div class="input-group has-validation">
                        <span class="input-group-text" id="inputGroupPrepend">@</span>
                        <g:textField placeholder='Username' name="username" class="form-control" id="username"/>
                        <div class="invalid-feedback">Please enter your username.</div>
                      </div>
                    </div>

                    <div class="col-12">
                      <label for="yourPassword" class="form-label">Password</label>
                      <g:passwordField placeholder='Password' name="password" class="form-control" id="password"/>
                      <div class="invalid-feedback">Please enter your password!</div>
                    </div>

                    <div class="col-12">
                      <g:if test="${session['post_login']}">
                          <g:hiddenField name='post_login' value="${session['post_login']}"/>
                      </g:if>
                      <button class="btn btn-primary w-100" type="submit">Login</button>
                    </div>
                    <g:if test="${grailsApplication.config.getProperty('server.allow_registration')=='true'}">
                    <div class="col-12">
                      <p class="small mb-0">Don't have account? <g:link controller='user' action='register'>Create an account</g:link></p>
                    </div>
                    </g:if>
                  </g:form>

                </div>
              </div>

            </div>
          </div>
        </div>

      </section>

    </div>
    </body>
</html>
