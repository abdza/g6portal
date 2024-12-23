<td id='renametd'>
<form hx-target='#explorepage' hx-post="<g:createLink action="rename" id="${this.portalFileManager.id}" params="[fname:params.fname]"/>">
<input type='text' name='newname' value='${filename}'/>
<button>Rename</button>
</form>
</td>
