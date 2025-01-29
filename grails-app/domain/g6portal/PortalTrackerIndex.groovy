package g6portal

import groovy.sql.Sql
import static grails.util.Holders.config

class PortalTrackerIndex {

    static belongsTo = [tracker:PortalTracker]

    static constraints = {
        name()
        tracker()
        fields(nullable:true,widget:'textarea')
    }

    static mapping = {
        fields type: 'text'
    }

    String name
    String fields

    def deleteIndex() {
        PortalTracker.withSession { sqlsession ->
            def datasource = sqlsession.connection()
            def sql = new Sql(datasource)
            def query = ""
            def tablename = tracker.data_table()
            def safename = name.replaceAll(" ","_")
            def delquery = "drop index if exists ix_" + safename + " on " + tablename
            try {
                sql.execute(delquery)
            }
            catch(Exception e){
                PortalErrorLog.record(null,null,'tracker','create index',e.toString() + " query: " + query,tracker.slug,tracker.module)
            }
        }
    }

    def updateDb() {

        PortalTracker.withSession { sqlsession ->
            def datasource = sqlsession.connection()
            def sql = new Sql(datasource)
            def query = ""
            def tablename = tracker.data_table()
            println "Fields in index:" + fields
            def totalindex = []
            def allfields = fields.tokenize(",")*.trim()
            allfields.each { lcfield->
                def cfield = PortalTrackerField.findByTrackerAndName(tracker,lcfield)
                if(cfield && !(cfield.field_type in ['Text Area','HasMany'])) {
                    totalindex << lcfield
                }
            }
            println "Totalindex: " + totalindex
            def safename = name.replaceAll(" ","_")
            def indexname = "ix_" + tablename + "_" + safename
            if(totalindex) {
                def delquery = "DROP INDEX IF EXISTS " + indexname
                if(config.dataSource.url.contains("jdbc:postgresql")){
                    query = "CREATE INDEX IF NOT EXISTS " + indexname + " ON " + tablename + " (" + totalindex.join(',') + ")"
                }
                else{
                    delquery = "DROP INDEX IF EXISTS " + indexname + " ON " + tablename
                    query = "if not exists (select * from sys.indexes where name='" + indexname + "' and object_id=object_id('" + tablename + "'))begin create nonclustered index " + indexname + " on [" + tablename + "] ([" + totalindex.join('],[') + "]); end"
                }
                try {
                    println "Del query:" + delquery
                    sql.execute(delquery)
                    println "Index query:" + query
                    sql.execute(query)
                }
                catch(Exception e){
                    println "Error running query: " + e
                    PortalErrorLog.record(null,null,'tracker','create index',e.toString() + " query: " + query,tracker.slug,tracker.module)
                }
            }
        }
    }
}
