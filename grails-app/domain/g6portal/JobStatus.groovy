package g6portal

class JobStatus {
    static constraints = {
        jobType(nullable: false)
        status(nullable: false, inList: ['RUNNING', 'COMPLETED', 'FAILED'])
        startTime(nullable: false)
        endTime(nullable: true)
        totalRecords(nullable: true)
        processedRecords(nullable: true)
        errorMessage(nullable: true, maxSize: 1000)
    }
    
    String jobType
    String status
    Date startTime
    Date endTime
    Integer totalRecords
    Integer processedRecords
    String errorMessage
    
    static mapping = {
        errorMessage type: 'text'
    }
}

