package g6portal

class JobStatus {
    static constraints = {
        jobType(nullable: false)
        // NOTE: the status column is varchar(9) on existing databases, which is exactly
        // the width of COMPLETED/CANCELLED. Any new state must fit in 9 characters or
        // the insert fails at runtime rather than at validation.
        status(nullable: false, inList: ['RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'])
        startTime(nullable: false)
        endTime(nullable: true)
        totalRecords(nullable: true)
        processedRecords(nullable: true)
        errorMessage(nullable: true, maxSize: 1000)
        moduleName(nullable: true)      // null = job covered every module
        skippedRecords(nullable: true)  // rows that could not be sized (file gone / no path)
        cancelRequested(nullable: true) // set by the UI; the job checks it between batches
    }

    String jobType
    String status
    Date startTime
    Date endTime
    Integer totalRecords
    Integer processedRecords
    String errorMessage
    String moduleName
    Integer skippedRecords
    Boolean cancelRequested = false

    static mapping = {
        errorMessage type: 'text'
    }
}

