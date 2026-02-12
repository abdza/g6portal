<g:if test="${value}"><a href="${createLink(controller:'fileLink',action:'download',params:[module:this.fileLink?.module,slug:this.fileLink?.slug])}">${value}</a></g:if>
