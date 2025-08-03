package g6portal

import grails.gorm.transactions.Transactional

@Transactional
class FileLinkUpdateService {

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

    // Enhanced service method with job tracking
    @Transactional
    def updateMissingFileSizesWithTracking() {
        // Check if JobStatus domain exists, if not, skip job tracking
        def jobStatus = null
        try {
            jobStatus = new JobStatus(
                jobType: 'FILE_SIZE_UPDATE',
                status: 'RUNNING',
                startTime: new Date(),
                totalRecords: FileLink.countBySize(null),
                processedRecords: 0
            ).save(flush: true)
        } catch (Exception e) {
            log.warn "JobStatus tracking not available, proceeding without tracking: ${e.message}"
        }

        def updatedCount = 0
        def batchSize = 50
        def offset = 0

        try {
            log.info "Starting tracked file size update job" + (jobStatus ? " (Job ID: ${jobStatus.id})" : "")

            while (true) {
                def fileLinksToUpdate = FileLink.createCriteria().list(max: batchSize, offset: offset) {
                    isNull('size')
                    order('id', 'asc')
                }

                if (!fileLinksToUpdate) {
                    break
                }

                fileLinksToUpdate.each { fileLink ->
                    try {
                        if (fileLink.path) {
                            def theFile = new File(fileLink.path)
                            if (theFile.exists()) {
                                fileLink.size = (int) theFile.length()
                                fileLink.save(flush: true, failOnError: true)
                                updatedCount++
                            }
                        }
                    } catch (Exception e) {
                        log.error "Failed to update size for FileLink ID ${fileLink.id}: ${e.message}", e
                    }
                }

                offset += batchSize

                // Update job progress if tracking is available
                if (jobStatus) {
                    jobStatus.processedRecords = updatedCount
                    jobStatus.save(flush: true)
                }

                Thread.sleep(100)
            }

            if (jobStatus) {
                jobStatus.status = 'COMPLETED'
                jobStatus.endTime = new Date()
                jobStatus.processedRecords = updatedCount
                jobStatus.save(flush: true)
            }

            log.info "Tracked file size update job completed" + (jobStatus ? " (Job ID: ${jobStatus.id})" : "") + ". Updated ${updatedCount} records."

        } catch (Exception e) {
            if (jobStatus) {
                jobStatus.status = 'FAILED'
                jobStatus.endTime = new Date()
                jobStatus.errorMessage = e.message
                jobStatus.save(flush: true)
            }
            log.error "Tracked file size update job failed" + (jobStatus ? " (Job ID: ${jobStatus.id})" : "") + ": ${e.message}", e
            throw e
        }

        return jobStatus ?: [updatedCount: updatedCount]
    }
}