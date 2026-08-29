<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>Import Preview - ${portalModule.name}</title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(action:'index')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="show" id="${portalModule.id}">Back to Module</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div class="col-12 content" role="main">
                    <h1>Import Preview: ${portalModule.name}</h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <p>
                        Review the changes below before importing. Lines starting with
                        <span style="color:#cf222e;">-</span> show the current state that will be replaced,
                        lines starting with <span style="color:#1a7f37;">+</span> show the incoming changes.
                        <br/>Importing files: <strong>${file_on ? 'Yes' : 'No'}</strong>,
                        staff roles: <strong>${staff_on ? 'Yes' : 'No'}</strong>,
                        trees: <strong>${tree_on ? 'Yes' : 'No'}</strong><g:if test="${tree_on && !staff_on}"> (structure only, no role holders)</g:if>
                        <g:if test="${menu_on}"><br/>This package carries menu entries &mdash; see the checkbox below the diff.</g:if>
                        <g:if test="${settings}">
                            <br/>Settings are listed separately below the diff, where each one that
                            would change can be kept or updated individually.
                        </g:if>
                    </p>
                    <g:if test="${diff}">
                        <g:render template="diffview" model="[diff:diff]"/>
                    </g:if>
                    <g:else>
                        <div class="message" role="status">No changes detected between the current module and the migration files.</div>
                    </g:else>
                    <g:form useToken="true" action="confirmimport" id="${portalModule.id}" method="POST">
                        <g:if test="${file_on}"><g:hiddenField name="files" value="on"/></g:if>
                        <g:if test="${staff_on}"><g:hiddenField name="staff" value="on"/></g:if>
                        <%-- Menus are a live checkbox rather than a hidden field: they change
                             shared portal navigation, so a portal with a curated menu can take
                             the module without it. Ticked by default, because the common case
                             is wanting the module to be reachable. --%>
                        <g:if test="${menu_on}">
                        <div class="fieldcontain" style="margin-bottom:1em;">
                            <label for="menus">
                                <input type="checkbox" name="menus" id="menus" value="on" checked="checked"/>
                                Add this module's menu entries
                            </label>
                            <span class="property-value">
                                Puts the module in the header megamenu and gives its pages a sidebar.
                                Entries this module added before are updated or removed to match;
                                anything you added by hand is left alone.
                            </span>
                        </div>
                        </g:if>
                        <g:if test="${settings}">
                        <h2>Settings</h2>
                        <g:set var="changedcount" value="${settings.count { it.status == 'changed' }}"/>
                        <g:set var="newcount" value="${settings.count { it.status == 'new' }}"/>
                        <g:set var="samecount" value="${settings.count { it.status == 'same' }}"/>
                        <p>
                            ${settings.size()} setting${settings.size() == 1 ? '' : 's'} in the migration files:
                            <strong>${changedcount}</strong> would change,
                            <strong>${newcount}</strong> new,
                            <strong>${samecount}</strong> already identical.
                            <g:if test="${changedcount > 0}">
                                <br/>Settings are often specific to this server — mailboxes, folder
                                paths, scheduler URLs — so each changed setting below defaults to
                                <strong>keeping the value already in use here</strong>. Choose
                                "Use value from migration files" for any you actually mean to update.
                            </g:if>
                        </p>
                        <table class="table" style="width:100%;">
                            <thead>
                                <tr>
                                    <th style="width:22%;">Setting</th>
                                    <th style="width:29%;">Current value on this server</th>
                                    <th style="width:29%;">Value in migration files</th>
                                    <th style="width:20%;">Action</th>
                                </tr>
                            </thead>
                            <tbody>
                            <g:each in="${settings}" var="s" status="i">
                                <tr>
                                    <td>
                                        <strong>${s.name}</strong>
                                        <g:if test="${s.module}"><br/><span style="color:#666;font-size:0.85em;">${s.module}</span></g:if>
                                    </td>
                                    <td style="word-break:break-word;">
                                        <g:if test="${s.status == 'new'}"><em style="color:#666;">not set</em></g:if>
                                        <g:else>${s.current}</g:else>
                                    </td>
                                    <td style="word-break:break-word;">${s.incoming}</td>
                                    <td>
                                        <g:if test="${s.status == 'changed'}">
                                            <input type="hidden" name="settingkey_${i}" value="${s.key}"/>
                                            <label style="display:block;">
                                                <input type="radio" name="settingchoice_${i}" value="keep" checked="checked"/>
                                                Keep current value
                                            </label>
                                            <label style="display:block;">
                                                <input type="radio" name="settingchoice_${i}" value="import"/>
                                                Use value from migration files
                                            </label>
                                        </g:if>
                                        <g:elseif test="${s.status == 'new'}">
                                            <span style="color:#1a7f37;">Will be created</span>
                                        </g:elseif>
                                        <g:else>
                                            <span style="color:#666;">No change</span>
                                        </g:else>
                                    </td>
                                </tr>
                            </g:each>
                            </tbody>
                        </table>
                        </g:if>
                        <fieldset class="form">
                            <div class="fieldcontain required">
                                <label for="remarks">Import Remarks<span class="required-indicator">*</span></label>
                                <g:textArea name="remarks" rows="4" cols="80" required="required" style="width:100%;" placeholder="Describe the changes being imported"/>
                            </div>
                        </fieldset>
                        <fieldset class="buttons">
                            <input class="delete" type="submit" value="Confirm Import" onclick="return confirm('Import module ${portalModule.name}? The changes shown above will be applied.');" />
                            <g:link class="list" action="show" id="${portalModule.id}">Cancel</g:link>
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
