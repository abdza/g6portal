<%-- Shared by create.gsp and edit.gsp. Replaces <f:all>, which would render the contacts
     association as an unusable dropdown of every user in the portal. --%>
<g:set var="pm" value="${this.portalModule}"/>
<g:set var="isnew" value="${!pm?.id}"/>
<fieldset class="form pm-form">
    <div class="fieldcontain required">
        <label for="name">Name (key) <span class="required-indicator">*</span></label>
        <g:if test="${isnew}">
            <input type="text" name="name" id="name" value="${pm?.name}" required/>
            <small class="pm-help">Lowercase key used by every page, tracker, role and setting, e.g. <code>cbmy_srms</code>. It cannot be changed later.</small>
        </g:if>
        <g:else>
            <%-- Read-only once created: every page, tracker, role, setting and file refers to the
                 module by this string, so renaming it would orphan all of them. --%>
            <input type="text" name="name" id="name" value="${pm?.name}" readonly/>
        </g:else>
    </div>
    <div class="fieldcontain">
        <label for="title">Title</label>
        <input type="text" name="title" id="title" value="${pm?.title}" maxlength="255" placeholder="Readable name shown to people"/>
    </div>
    <div class="fieldcontain">
        <label for="status">Status</label>
        <select name="status" id="status">
            <option value="">Not recorded</option>
            <g:each in="${g6portal.PortalModule.STATUSES}" var="st">
                <option value="${st}" ${pm?.status == st ? 'selected' : ''}>${st}</option>
            </g:each>
        </select>
    </div>
    <div class="fieldcontain">
        <label for="category">Category</label>
        <%-- options: setting portal.module_categories, one per line (free text when unset) --%>
        <g:choiceSelect name="category" value="${pm?.category}" setting="portal.module_categories" maxlength="100"/>
    </div>
    <div class="fieldcontain">
        <label for="department">Department</label>
        <%-- options: setting portal.module_departments, one per line (free text when unset) --%>
        <g:choiceSelect name="department" value="${pm?.department}" setting="portal.module_departments" maxlength="255"/>
    </div>

    <%-- tells the controller the pickers were on the form, so an empty selection means
         "remove them all" rather than "not submitted" --%>
    <input type="hidden" name="contactsSubmitted" value="1"/>
    <div class="fieldcontain" id="ownerIds_div">
        <label for="ownerIds">Owners</label>
        <select name="ownerIds" id="ownerIds" multiple style="width: 60%;">
            <g:each in="${pm?.owners()}" var="u"><option value="${u.id}" selected>${u.name}</option></g:each>
        </select>
        <small class="pm-help">Accountable for the module; access reviews go to them.</small>
    </div>
    <div class="fieldcontain" id="maintainerIds_div">
        <label for="maintainerIds">Maintainers</label>
        <select name="maintainerIds" id="maintainerIds" multiple style="width: 60%;">
            <g:each in="${pm?.maintainers()}" var="u"><option value="${u.id}" selected>${u.name}</option></g:each>
        </select>
        <small class="pm-help">Build and support it.</small>
    </div>

    <div class="fieldcontain">
        <label for="description">Description</label>
        <textarea name="description" id="description" rows="4" maxlength="4000" placeholder="What the module does">${pm?.description}</textarea>
    </div>
    <div class="fieldcontain">
        <label for="benefit">Benefit</label>
        <textarea name="benefit" id="benefit" rows="4" maxlength="4000" placeholder="What it gives the business">${pm?.benefit}</textarea>
    </div>
</fieldset>
<style>
    .pm-form textarea { width: 60%; }
    .pm-form .pm-help { display: block; margin-left: 25%; color: #6b7280; font-size: .8rem; }
    @media (max-width: 760px) { .pm-form .pm-help { margin-left: 0; } .pm-form textarea { width: 100%; } }
</style>
<asset:script>
    <g:user_selector property="ownerIds" parent="#ownerIds_div"/>
    <g:user_selector property="maintainerIds" parent="#maintainerIds_div"/>
</asset:script>
