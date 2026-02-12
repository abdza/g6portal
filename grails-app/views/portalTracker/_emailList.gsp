<table>
    <thead>
        <tr>
            <th>#</th>
            <th>Name</th>
            <th>Email To</th>
            <th>Transition</th>
            <th>Status</th>
        </tr>
    </thead>
    <tbody>
    <g:each in="${portalTracker.emails.sort{it.name}}" status="i" var="email">
        <tr class="${(i % 2) == 0 ? 'even' : 'odd'}">
            <td>${i+1}</td>
            <td><g:link controller="portalTrackerEmail" action="show" id="${email.id}">${email.name}</g:link></td>
            <td>${email.emailto}</td>
            <td>${email.transition}</td>
            <td>${email.status}</td>
        </tr>
    </g:each>
    </tbody>
</table>
<g:link controller="portalTrackerEmail" action="create" params="[tracker:portalTracker.id]">Create Email</g:link>
