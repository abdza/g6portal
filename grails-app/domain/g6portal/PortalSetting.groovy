package g6portal

class PortalSetting {

   static constraints = {
        name()
        module(nullable:true)
        type(nullable:true,inList:['Text','Array','Hashmap','Date','Number','Xml'])
        text(nullable:true,maxSize:5000,widget:'textarea')
        date_value(nullable:true)
        number(nullable:true)
        datum_type(nullable:true,inList:['String','Integer','Float','Date','DateTime'])
    }

    static mapping = {
        text type: 'text'
        cache true
    }

    String name
    String text
    Date date_value
    Integer number
    String type
    String module
    String datum_type

    def value(){
        if(type=='Text'){
            return text
        }
        else if(type=='Array'){
            if(text){
                def data_toret = text.tokenize(',')*.trim()
                def toret = []
                if(datum_type) {
                    if(datum_type=='Float') {
                        data_toret.each { v ->
                            toret << new BigDecimal(v).floatValue()
                        }
                    }
                    else if(datum_type=='Integer') {
                        data_toret.each { v ->
                            toret << v.toInteger()
                        }
                    }
                    else if(datum_type=='Date') {
                        data_toret.each { v ->
                            toret << Date.parse("yyyy-MM-dd",v)
                        }
                    }
                    else if(datum_type=='DateTime') {
                        data_toret.each { v ->
                            toret << Date.parse("yyyy-MM-dd H:m:s",v)
                        }
                    }
                    else {
                        toret = data_toret
                    }
                }
                else {
                    toret = data_toret
                }
                return toret
            }
            else{
                return []
            }
        }
        else if(type=='Hashmap'){
            def toreturn = [:]
            def tokens = text.tokenize(',')*.trim()
            tokens.each { token->
                def btoken = token.tokenize('|')
                if(btoken.size()>1){
                    toreturn[btoken[0]] = btoken[1]
                    if(datum_type) {
                        if(datum_type=='Float') {
                            toreturn[btoken[0]] = new BigDecimal(btoken[1]).floatValue()
                        }
                        else if(datum_type=='Integer') {
                            toreturn[btoken[0]] = btoken[1].toInteger()
                        }
                        else if(datum_type=='Date') {
                            toreturn[btoken[0]] = Date.parse("yyyy-MM-dd",btoken[1])
                        }
                        else if(datum_type=='DateTime') {
                            toreturn[btoken[0]] = Date.parse("yyyy-MM-dd H:m:s",btoken[1])
                        }
                    }
                }
                else{
                    toreturn[btoken[0]] = btoken[0]
                }
            }
            return toreturn
        }
        else if(type=='Date'){
            return date_value
        }
        else if(type=='Number'){
            return number
        }
        else if(type=='Xml'){
            return new XmlSlurper().parseText(text)
        }
        return text
    }

    static namedefault(name,defaultval=null){
        def btoken = name.tokenize('.')
        def cur = null
        if(btoken.size()>1){
            cur = findByModuleAndName(btoken[0],btoken[1])
            if(!cur) {
                cur = findByName(name)
            }
        }
        else {
            cur = findByName(btoken[0])
        }
        if(cur){
            return cur.value()
        }
        else{
            return defaultval
        }
    }
}
