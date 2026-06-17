<style>
    .module-diff {
        font-family: monospace;
        font-size: 13px;
        line-height: 1.4;
        background: #fff;
        border: 1px solid #ccc;
        border-radius: 4px;
        padding: 10px;
        max-height: 600px;
        overflow: auto;
        white-space: pre;
    }
    .module-diff .diff-add { background: #e6ffec; color: #1a7f37; display: block; }
    .module-diff .diff-del { background: #ffebe9; color: #cf222e; display: block; }
    .module-diff .diff-hunk { background: #ddf4ff; color: #0550ae; display: block; }
    .module-diff .diff-file { background: #f6f8fa; color: #57606a; font-weight: bold; display: block; }
    .module-diff .diff-ctx { display: block; }
</style>
<div class="module-diff"><g:each in="${diff.split('\n', -1)}" var="line"><g:if test="${line.startsWith('---') || line.startsWith('+++') || line.startsWith('Binary file')}"><span class="diff-file">${line}</span></g:if><g:elseif test="${line.startsWith('@@')}"><span class="diff-hunk">${line}</span></g:elseif><g:elseif test="${line.startsWith('+')}"><span class="diff-add">${line}</span></g:elseif><g:elseif test="${line.startsWith('-')}"><span class="diff-del">${line}</span></g:elseif><g:else><span class="diff-ctx">${line}</span></g:else></g:each></div>
