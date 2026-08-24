<fieldset class="form">
    <div class="fieldcontain required">
        <label for="module">Module <span class="required-indicator">*</span></label>
        <g:textField name="module" value="${portalEndpoint?.module}" required="required"/>
        <span class="property-value">First half of the URL: <code>/svc/<b>module</b>/slug</code></span>
    </div>
    <div class="fieldcontain required">
        <label for="slug">Slug <span class="required-indicator">*</span></label>
        <g:textField name="slug" value="${portalEndpoint?.slug}" required="required"/>
        <span class="property-value">Second half: <code>/svc/module/<b>slug</b></code></span>
    </div>
    <div class="fieldcontain">
        <label for="name">Name</label>
        <g:textField name="name" value="${portalEndpoint?.name}"/>
    </div>
    <div class="fieldcontain">
        <label for="description">Description</label>
        <g:textArea name="description" value="${portalEndpoint?.description}" rows="2" cols="60"/>
    </div>
    <div class="fieldcontain required">
        <label for="handler_type">Handler <span class="required-indicator">*</span></label>
        <g:select name="handler_type" from="${['CGI','Proxy']}" value="${portalEndpoint?.handler_type}"/>
        <span class="property-value">CGI runs a program per RFC 3875; Proxy forwards to an upstream URL.</span>
    </div>
    <div class="fieldcontain required">
        <label for="target">Target <span class="required-indicator">*</span></label>
        <g:textField name="target" value="${portalEndpoint?.target}" required="required" size="80"/>
        <span class="property-value">
            CGI: the program to run, e.g. <code>/usr/lib/git-core/git-http-backend</code>.
            It is split on spaces so an interpreter can come first
            (<code>python.exe hgweb.cgi</code>) — <strong>so quote a path that contains a
            space</strong>: <code>"C:\Program Files\Git\mingw64\libexec\git-core\git-http-backend.exe"</code>.
            Proxy: the upstream base URL.
        </span>
    </div>
    <div class="fieldcontain">
        <label for="working_dir">Working directory</label>
        <g:textField name="working_dir" value="${portalEndpoint?.working_dir}" size="60"/>
    </div>
    <div class="fieldcontain">
        <label for="env_json">Environment (JSON)</label>
        <g:textArea name="env_json" value="${portalEndpoint?.env_json}" rows="3" cols="60"/>
        <span class="property-value">e.g. <code>{"GIT_PROJECT_ROOT":"/srv/scmrepos","GIT_HTTP_EXPORT_ALL":"1"}</code></span>
    </div>
    <div class="fieldcontain">
        <label for="header_env_json">Header → environment (JSON)</label>
        <g:textArea name="header_env_json" value="${portalEndpoint?.header_env_json}" rows="2" cols="60"/>
        <span class="property-value">
            Headers already arrive as <code>HTTP_*</code>. This is for programs that insist on a
            bare name, e.g. <code>{"Git-Protocol":"GIT_PROTOCOL"}</code>.
        </span>
    </div>
    <div class="fieldcontain required">
        <label for="auth_mode">Auth mode <span class="required-indicator">*</span></label>
        <g:select name="auth_mode" from="${['None','Session','Basic','Token']}" value="${portalEndpoint?.auth_mode}"/>
        <span class="property-value">
            Basic is what git and hg clients send. Session suits browsers. None is open to
            anyone who can reach the URL.
        </span>
    </div>
    <div class="fieldcontain">
        <label for="auth_token">Token</label>
        <g:textField name="auth_token" value="${portalEndpoint?.auth_token}" size="60"/>
        <span class="property-value">
            Only for auth mode Token. Never leaves this server — module exports omit it.
        </span>
    </div>
    <div class="fieldcontain">
        <label for="realm">Basic realm</label>
        <g:textField name="realm" value="${portalEndpoint?.realm}"/>
    </div>
    <div class="fieldcontain">
        <label for="allowed_roles">Allowed roles</label>
        <g:textField name="allowed_roles" value="${portalEndpoint?.allowed_roles}" size="60"/>
        <span class="property-value">Comma separated, in this endpoint's module. Empty means any authenticated user.</span>
    </div>
    <div class="fieldcontain">
        <label for="timeout_seconds">Timeout (seconds)</label>
        <g:field type="number" name="timeout_seconds" value="${portalEndpoint?.timeout_seconds}"/>
    </div>
    <div class="fieldcontain">
        <label for="max_body_mb">Max body (MB)</label>
        <g:field type="number" name="max_body_mb" value="${portalEndpoint?.max_body_mb}"/>
    </div>
    <div class="fieldcontain">
        <label for="enabled">Enabled</label>
        <g:checkBox name="enabled" value="${portalEndpoint?.enabled}"/>
        <span class="property-value">Disabled endpoints are ignored entirely — nothing is served.</span>
    </div>
</fieldset>
