<ol class="property-list ${domainClass.decapitalizedName}">
    <g:each in="${domainProperties}" var="p">
        <li class="fieldcontain">
            <span id="${p.name}-label" class="property-label"><g:message code="${domainClass.decapitalizedName}.${p.name}.label" default="${p.defaultLabel}" /></span>
            <div class="property-value" aria-labelledby="${p.name}-label">
            <f:display bean="${domainClass.decapitalizedName}" property="${p.name}"/>
            </div>
        </li>
    </g:each>
</ol>