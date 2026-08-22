package g6portal

import grails.gorm.transactions.Transactional
import org.springframework.transaction.annotation.Propagation
import groovy.sql.Sql

@Transactional
class FileLinkUpdateService {

    def dataSource


    // Method to get progress information
    @Transactional(readOnly = true)
    def getSizeUpdateProgress() {
        def total = FileLink.count()
        def missing = FileLink.countBySize(null)
        def updated = total - missing

        return [
            total: total,
            updated: updated,
            missing: missing,
            percentComplete: total > 0 ? (updated / total * 100).round(2) : 0
        ]
    }

    // Alternative method for checking progress
    @Transactional(readOnly = true)
    def getMissingSizeCount() {
        return FileLink.countBySize(null)
    }

    @Transactional
    def updateMissingFileSizes() {
        def updatedCount = 0
        def batchSize = 50
        def offset = 0

        log.info "Starting file size update job for FileLink records missing size information"

        while (true) {
            // Get batch of FileLink records where size is null
            def fileLinksToUpdate = FileLink.createCriteria().list(max: batchSize, offset: offset) {
                isNull('size')
                order('id', 'asc')
            }

            if (!fileLinksToUpdate) {
                break // No more records to process
            }

            log.info "Processing batch ${offset/batchSize + 1}: ${fileLinksToUpdate.size()} records"

            fileLinksToUpdate.each { fileLink ->
                try {
                    if (fileLink.path) {
                        def theFile = new File(fileLink.path)
                        if (theFile.exists()) {
                            fileLink.size = (int) theFile.length()
                            fileLink.save(flush: true, failOnError: true)
                            updatedCount++
                            log.debug "Updated FileLink ID ${fileLink.id} with size ${fileLink.size} bytes"
                        } else {
                            log.warn "File does not exist for FileLink ID ${fileLink.id}: ${fileLink.path}"
                        }
                    } else {
                        log.warn "No path specified for FileLink ID ${fileLink.id}"
                    }
                } catch (Exception e) {
                    log.error "Failed to update size for FileLink ID ${fileLink.id}: ${e.message}", e
                }
            }

            offset += batchSize

            // Optional: Add a small delay to prevent overwhelming the system
            Thread.sleep(100)
        }

        log.info "File size update job completed. Updated ${updatedCount} FileLink records."
        return updatedCount
    }

    /**
     * File size backfill with JobStatus tracking, safe to run against a large production
     * table from a background thread.
     *
     * Design notes, all of which matter at 5M+ rows:
     *  - Plain SQL, not GORM. Streaming millions of entities through the Hibernate session
     *    grows the first-level cache without bound (OOM), and thrashes the second-level
     *    cache that FileLink is mapped with, which is what other users would actually feel.
     *  - Keyset pagination (id > lastId), not OFFSET. Rows leave the "size is null" set as
     *    they are fixed, so an advancing OFFSET both skips records and degrades to O(n^2)
     *    as each batch rescans everything already done.
     *  - Batched UPDATEs rather than one flushed save per row, to cut round trips and
     *    transaction-log churn.
     *  - Deliberately NOT @Transactional: a multi-hour enclosing transaction would withhold
     *    every progress write until the end, so the status page would sit at 0 then jump
     *    to COMPLETED.
     *  - Progress and the cancel flag are read/written over the same JDBC connection rather
     *    than through GORM. A task{} thread has no Hibernate session to begin with, and a
     *    session-cached JobStatus would go stale and never observe the cancel flag that the
     *    request thread sets.
     *  - Cancellable and throttled, so it can be stopped and paced on a live system.
     *
     * The JobStatus row is created by the caller on the request thread and its id passed
     * in, because a task{} thread has no Hibernate session and GORM dynamic finders fail
     * there with "No Session found for current thread" even inside withNewSession.
     *
     * Batch size and delay are passed in rather than read here for the same reason:
     * PortalSetting.namedefault() is a GORM dynamic finder and would fail on this thread.
     *
     * @param jobId      - id of the pre-created JobStatus row, or null to run untracked
     * @param moduleName - restrict to one module, or null for every module
     * @param batchSize  - rows fetched and updated per iteration
     * @param delayMs    - pause between batches, to pace load on a live system
     * @return Long - the jobId that was tracked
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    def updateMissingFileSizesWithTracking(Long jobId, String moduleName = null,
                                           int batchSize = 500, int delayMs = 100) {
        def sizedCount = 0
        def skippedCount = 0
        def cancelled = false

        if (batchSize < 1) batchSize = 500
        if (delayMs < 0) delayMs = 0

        // Standard Grails naming: FileLink -> file_link, JobStatus -> job_status.
        // 'size' is not a reserved word in either MSSQL or Postgres, so it needs no quoting.
        def selectSql = 'select id, path from file_link where size is null and id > :lastId' +
                        (moduleName ? ' and module = :moduleName' : '') +
                        ' order by id offset 0 rows fetch next ' + batchSize + ' rows only'

        def sql = new Sql(dataSource)

        def readCancel = { ->
            if (!jobId) return false
            try {
                def row = sql.firstRow('select cancel_requested from job_status where id = :id', [id: jobId])
                return row?.getAt('cancel_requested') ? true : false
            } catch (Exception e) {
                log.warn "Could not read cancel flag for job ${jobId}: ${e.message}"
                return false
            }
        }

        def writeProgress = { int sized, int skipped, String status = null, String err = null ->
            if (!jobId) return
            try {
                def sets = ['processed_records = :sized', 'skipped_records = :skipped']
                def qp = [sized: sized, skipped: skipped, id: jobId]
                if (status) {
                    sets << 'status = :status'
                    sets << 'end_time = :endtime'
                    qp.status = status
                    qp.endtime = new java.sql.Timestamp(System.currentTimeMillis())
                }
                if (err) {
                    sets << 'error_message = :err'
                    qp.err = err.take(1000)   // column is capped at 1000
                }
                sql.executeUpdate('update job_status set ' + sets.join(', ') + ' where id = :id', qp)
            } catch (Exception e) {
                log.warn "Could not update JobStatus ${jobId}: ${e.message}"
            }
        }

        try {
            log.info "Starting file size backfill" + (jobId ? " (Job ID: ${jobId})" : "") +
                     (moduleName ? " for module '${moduleName}'" : " for all modules")

            long lastId = 0L
            while (true) {
                if (readCancel()) {
                    cancelled = true
                    break
                }

                def qparams = [lastId: lastId]
                if (moduleName) qparams.moduleName = moduleName
                def rows = sql.rows(selectSql, qparams)
                if (!rows) {
                    break
                }

                def updates = []
                rows.each { r ->
                    // Advance the cursor past every row we look at, including ones we
                    // cannot size. They keep size null, so an id-based cursor is what
                    // stops them being re-read forever.
                    lastId = (r.getAt('id') as Number).longValue()
                    def filepath = r.getAt('path')
                    if (filepath) {
                        try {
                            def thefile = new File(filepath.toString())
                            if (thefile.exists()) {
                                updates << [fsize: (int) thefile.length(), fid: r.getAt('id')]
                            } else {
                                skippedCount++
                            }
                        } catch (Exception e) {
                            log.warn "Could not stat file for FileLink ID ${r.getAt('id')}: ${e.message}"
                            skippedCount++
                        }
                    } else {
                        skippedCount++
                    }
                }

                if (updates) {
                    sql.withBatch(100, 'update file_link set size = :fsize where id = :fid') { stmt ->
                        updates.each { stmt.addBatch(it) }
                    }
                    sizedCount += updates.size()
                }

                writeProgress(sizedCount, skippedCount)
                if (delayMs > 0) {
                    Thread.sleep(delayMs)
                }
            }

            writeProgress(sizedCount, skippedCount, cancelled ? 'CANCELLED' : 'COMPLETED')
            log.info "File size backfill ${cancelled ? 'cancelled' : 'completed'}" +
                     (jobId ? " (Job ID: ${jobId})" : "") +
                     ". Sized ${sizedCount}, could not size ${skippedCount}."

        } catch (Exception e) {
            writeProgress(sizedCount, skippedCount, 'FAILED', e.message)
            log.error "File size backfill failed" + (jobId ? " (Job ID: ${jobId})" : "") + ": ${e.message}", e
        } finally {
            try { sql.close() } catch (Exception ignored) { }
        }

        return jobId
    }

    /**
     * Disk-usage summary for one module: total bytes, file count, and how many rows have
     * no size recorded (which means the total understates real usage).
     *
     * Uses SQL with an explicit bigint cast rather than a criteria sum(): FileLink.size is
     * an int column, and MSSQL's SUM() over int returns int, so any module holding more
     * than 2GB overflows with "Arithmetic overflow error converting expression to data
     * type int". The cast works on both MSSQL and Postgres.
     *
     * @return Map with keys size (Long, may be null), count (int), unsized (int)
     */
    Map moduleFileStats(String moduleName) {
        def sql = new Sql(dataSource)
        try {
            def row = sql.firstRow(
                'select sum(cast(size as bigint)) as total_size, ' +
                '       count(id) as file_count, ' +
                '       sum(case when size is null then 1 else 0 end) as unsized_count ' +
                'from file_link where module = :m', [m: moduleName])
            return [
                size   : row?.getAt('total_size'),
                count  : row?.getAt('file_count') ?: 0,
                unsized: row?.getAt('unsized_count') ?: 0
            ]
        } catch (Exception e) {
            log.error "Could not compute file stats for module ${moduleName}: ${e.message}", e
            return [size: null, count: 0, unsized: 0]
        } finally {
            try { sql.close() } catch (Exception ignored) { }
        }
    }

    /**
     * Creates the JobStatus row for a backfill run. Lives here rather than in the
     * controller because this service is @Transactional and controller actions are not.
     * @return Long - id of the new row
     */
    Long createBackfillJob(String moduleName) {
        def remaining = moduleName ? FileLink.countByModuleAndSizeIsNull(moduleName)
                                   : FileLink.countBySize(null)
        return new JobStatus(
            jobType: 'FILE_SIZE_UPDATE',
            moduleName: moduleName,
            status: 'RUNNING',
            startTime: new Date(),
            totalRecords: remaining,
            processedRecords: 0,
            skippedRecords: 0,
            cancelRequested: false
        ).save(flush: true).id
    }

    /**
     * Flags a running job for cancellation. The job observes this between batches.
     * @return String - null on success, otherwise the reason it could not be cancelled
     */
    String requestCancel(Long jobId) {
        def js = jobId ? JobStatus.get(jobId) : null
        if (!js) {
            return "Job not found."
        }
        if (js.status != 'RUNNING') {
            return "Job #${js.id} is not running (status ${js.status})."
        }
        if (js.cancelRequested) {
            // Already asked politely once. A worker whose JVM was restarted mid-run leaves
            // its row RUNNING forever, which would block every future job, so a second
            // press closes the row out directly.
            js.status = 'CANCELLED'
            js.endTime = new Date()
            js.errorMessage = 'Force-stopped: worker did not respond to the cancellation request.'
            js.save(flush: true)
            return null
        }
        js.cancelRequested = true
        js.save(flush: true)
        return null
    }

    private static int asInt(value, int fallback) {
        try {
            return value == null ? fallback : (value as Integer)
        } catch (Exception ignored) {
            return fallback
        }
    }
}
