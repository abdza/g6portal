<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="empty" />
        <title>Set Up This Portal</title>
    </head>
    <body>
    <div id="content" role="main">
      <section class="section register min-vh-100 d-flex flex-column align-items-center justify-content-center py-4">
        <div class="container">
          <div class="row justify-content-center">
            <div class="col-lg-6 col-md-8 d-flex flex-column align-items-center justify-content-center">

              <div class="card mb-3">
                <div class="card-body">

                  <div class="pt-4 pb-2">
                    <h5 class="card-title text-center pb-0 fs-4">Set Up This Portal</h5>
                    <p class="text-center small">This instance has no administrator yet. Create the first one below.</p>
                  </div>

                  <g:if test="${flash.message}">
                    <div class="alert alert-warning" role="status">${flash.message}</div>
                  </g:if>

                  <p class="small">
                    The setup token was printed to the server console at startup and is stored in
                    <code>${tokenpath}</code> on the machine running the portal. This page stops
                    working as soon as an administrator exists.
                  </p>

                  <g:form useToken="true" controller="setup" action="complete" method="POST" class="row g-3">

                    <div class="col-12">
                      <label for="token" class="form-label">Setup Token</label>
                      <g:field type="text" name="token" id="token" class="form-control" required="required" autocomplete="off"/>
                    </div>

                    <div class="col-12">
                      <label for="userid" class="form-label">User ID (this is the login username)</label>
                      <g:textField name="userid" id="userid" class="form-control" required="required"
                                   value="${params.userid}"/>
                    </div>

                    <div class="col-12">
                      <label for="name" class="form-label">Full Name</label>
                      <g:textField name="name" id="name" class="form-control" required="required" value="${params.name}"/>
                    </div>

                    <div class="col-12">
                      <label for="email" class="form-label">Email</label>
                      <g:field type="email" name="email" id="email" class="form-control" required="required" value="${params.email}"/>
                    </div>

                    <div class="col-12">
                      <label for="password" class="form-label">Password</label>
                      <g:passwordField name="password" id="password" class="form-control" required="required" minlength="8"/>
                    </div>

                    <div class="col-12">
                      <label for="password2" class="form-label">Repeat Password</label>
                      <g:passwordField name="password2" id="password2" class="form-control" required="required" minlength="8"/>
                    </div>

                    <div class="col-12">
                      <div class="form-check">
                        <input class="form-check-input" type="checkbox" name="enablesuperuser" id="enablesuperuser" checked="checked"/>
                        <label class="form-check-label" for="enablesuperuser">
                          Treat system administrators as Admin of every module
                          <span class="small">(sets the <code>enablesuperuser</code> setting - recommended for a new instance)</span>
                        </label>
                      </div>
                    </div>

                    <div class="col-12">
                      <button class="btn btn-primary w-100" type="submit">Create Administrator</button>
                    </div>
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
