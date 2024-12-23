<div id='outer${property}'><div id='${property}div'>${value.encodeAsHTML()}</div></div>
<input type="hidden" id="${property}" name="${property}" value="${value.encodeAsHTML()}"/>
<style type="text/css" media="screen">
    #${property}div { 
        position: absolute;
        top: 0;
        right: 0;
        bottom: 0;
        left: 0;
    }
.ace_editor, .ace_editor *
{
    font-family: "Monaco", "Menlo", "Ubuntu Mono", "Droid Sans Mono", "Consolas", monospace !important;
    font-size: 12px !important;
    font-weight: 400 !important;
    letter-spacing: 0 !important;
}

#outer${property} {
widht: 68%;
height: 500px;
position: relative;
}

form .ace_editor.fullScreen {
	height: auto;
	width: auto;
	border: 0;
	margin: 0;
	position: fixed !important;
	top: 0;
	bottom: 0;
	left: 0;
	right: 0;
	z-index: 1000000;
}

body.fullScreen {
	overflow: hidden;
	transform: none!important;
}
</style>

  <asset:script type='text/javascript'>
$(document).ready(function(){
  var ${property}editor = ace.edit("${property}div");
  ${property}editor.setTheme("ace/theme/github");
<g:set var="curuser" value="${g6portal.User.get(session?.userid)}"/>
<g:if test='${g6portal.PortalSetting.namedefault(curuser.userID + '_editor','normal')=='vim'}'>
  ${property}editor.setKeyboardHandler("ace/keyboard/vim");
  ace.config.loadModule('ace/keyboard/vim', function(module) {
   var VimApi = module.CodeMirror.Vim;
   VimApi.defineEx('write', 'w', function(cm, input) {
      cm.ace.execCommand('save');
      console.log('saving');
      document.getElementById("updatebutton").click();
   });
});
</g:if>
  var htmlmode = ace.require("ace/mode/html").Mode;
  var groovymode = ace.require("ace/mode/groovy").Mode;
  $('.content form').on('submit',function(){
         document.getElementById('${property}').value = ${property}editor.getValue();
  });
  updatemode(${property}editor);
  $('#runable').on('change',function(){
	updatemode(${property}editor);
    });
  ${property}editor.commands.addCommand({
	name: "Toggle Fullscreen",
	bindKey: "F11",
	exec: function(${property}editor) {
		$('body').toggleClass('fullScreen');
		$('.ace_editor').toggleClass('fullScreen');
		${property}editor.setAutoScrollEditorIntoView(true);
		${property}editor.resize();
	}
  });
});

  function updatemode(editor) {
	var htmlmode = ace.require("ace/mode/html").Mode;
	var groovymode = ace.require("ace/mode/groovy").Mode;
  editor.session.setMode(new htmlmode());
  }
  </asset:script>
