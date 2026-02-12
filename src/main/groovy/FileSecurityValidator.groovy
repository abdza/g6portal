package g6portal

import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

/**
 * File Security Validator - Provides secure file upload validation
 * Prevents malicious file uploads through content type checking, size limits,
 * and path traversal prevention
 *
 * Special handling for password-protected Excel files:
 * - Regular XLSX files have ZIP signatures (PK...)
 * - Password-protected XLSX files have OLE2 signatures (D0 CF 11 E0...)
 * - Both XLS and password-protected XLSX use OLE2 format
 * - The validator now allows both formats for Excel files
 */
class FileSecurityValidator {

    // Maximum file size (50MB default)
    static final long MAX_FILE_SIZE = 50L * 1024L * 1024L

    // Magic number signatures for allowed file types
    static final Map<String, List<byte[]>> ALLOWED_SIGNATURES = [
        'pdf': [
            [0x25, 0x50, 0x44, 0x46] as byte[]  // %PDF
        ],
        'jpg': [
            [0xFF, 0xD8, 0xFF] as byte[]        // JPEG
        ],
        'jpeg': [
            [0xFF, 0xD8, 0xFF] as byte[]        // JPEG
        ],
        'png': [
            [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A] as byte[]  // PNG
        ],
        'gif': [
            [0x47, 0x49, 0x46, 0x38, 0x37, 0x61] as byte[], // GIF87a
            [0x47, 0x49, 0x46, 0x38, 0x39, 0x61] as byte[]  // GIF89a
        ],
        'zip': [
            [0x50, 0x4B, 0x03, 0x04] as byte[],  // ZIP
            [0x50, 0x4B, 0x05, 0x06] as byte[],  // ZIP (empty)
            [0x50, 0x4B, 0x07, 0x08] as byte[]   // ZIP (spanned)
        ],
        'xls': [
            [0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1] as byte[]  // OLE2
        ],
        'xlsx': [
            [0x50, 0x4B, 0x03, 0x04] as byte[],  // ZIP-based (Office 2007+)
            [0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1] as byte[]  // OLE2 (password-protected)
        ],
        'doc': [
            [0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1] as byte[]  // OLE2
        ],
        'docx': [
            [0x50, 0x4B, 0x03, 0x04] as byte[]   // ZIP-based (Office 2007+)
        ],
        'txt': [
            // Text files don't have reliable magic numbers, validate by content
        ],
        'csv': [
            // CSV files don't have magic numbers, validate by content
        ],
        'css': [
            // CSS files don't have magic numbers, validate by content
        ],
        'js': [
            // JS files don't have magic numbers, validate by content
        ],
        'ppt': [
            [0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1] as byte[]  // OLE2
        ],
        'pptx': [
            [0x50, 0x4B, 0x03, 0x04] as byte[]   // ZIP-based (Office 2007+)
        ]
    ]

    // Dangerous file extensions to always block
    static final Set<String> BLOCKED_EXTENSIONS = [
        'exe', 'bat', 'cmd', 'com', 'pif', 'scr', 'vbs', 'vbe', 'jar',
        'sh', 'bash', 'csh', 'ksh', 'fish', 'pl', 'py', 'rb', 'php', 'asp',
        'aspx', 'jsp', 'war', 'ear', 'class', 'dex', 'so', 'dll', 'sys',
        'msi', 'dmg', 'pkg', 'deb', 'rpm', 'app', 'ipa', 'apk'
    ]

    // Pattern for detecting path traversal attempts
    static final Pattern PATH_TRAVERSAL_PATTERN = ~/(?i)(\.\.[\/\\]|[\/\\]\.\.|\.\.%2f|\.\.%5c|%2e%2e%2f|%2e%2e%5c)/

    /**
     * Validates file upload security
     * @param file MultipartFile to validate
     * @param allowedTypes List of allowed file extensions (optional)
     * @param maxSize Maximum file size in bytes (optional)
     * @return Map with validation results
     */
    static Map<String, Object> validateFile(def file, List<String> allowedTypes = null, Long maxSize = null) {

        def result = [
            valid: false,
            errors: [],
            warnings: []
        ]

        if (!file || file.empty) {
            result.errors << "No file provided or file is empty"
            return result
        }

        String originalFilename = file.originalFilename
        if (!originalFilename) {
            result.errors << "Filename is required"
            return result
        }

        // Check file size
        long fileSize = file.size
        long sizeLimit = maxSize ?: MAX_FILE_SIZE
        if (fileSize > sizeLimit) {
            result.errors << "File size (${formatFileSize(fileSize)}) exceeds maximum allowed size (${formatFileSize(sizeLimit)})"
        }

        // Sanitize and validate filename
        String sanitizedFilename = sanitizeFilename(originalFilename)
        if (!sanitizedFilename) {
            result.errors << "Invalid filename after sanitization"
            return result
        }

        // Check for path traversal attempts
        if (containsPathTraversal(originalFilename)) {
            result.errors << "Filename contains path traversal attempts"
        }

        // Extract file extension
        String extension = getFileExtension(sanitizedFilename)
        if (!extension) {
            result.errors << "File must have a valid extension"
            return result
        }

        // Check if extension is blocked
        if (BLOCKED_EXTENSIONS.contains(extension.toLowerCase())) {
            result.errors << "File type '${extension}' is not allowed for security reasons"
        }

        // Check against allowed types if specified
        if (allowedTypes && !allowedTypes.contains(extension.toLowerCase())) {
            result.errors << "File type '${extension}' is not in the allowed types: ${allowedTypes.join(', ')}"
        }

        // Validate file content using magic numbers
        if (!validateFileContent(file, extension)) {
            result.errors << "File content does not match the file extension '${extension}'"
        }

        // Check for suspicious patterns in filename
        if (containsSuspiciousPatterns(sanitizedFilename)) {
            result.warnings << "Filename contains potentially suspicious patterns"
        }

        result.valid = result.errors.isEmpty()
        result.sanitizedFilename = sanitizedFilename
        result.detectedType = extension.toLowerCase()
        result.fileSize = fileSize

        return result
    }

    /**
     * Sanitizes filename by removing dangerous characters
     */
    static String sanitizeFilename(String filename) {
        if (!filename) return null

        // Remove or replace dangerous characters
        String sanitized = filename
            .replaceAll(/[<>:"|?*]/, '_')  // Windows reserved chars
            .replaceAll(/[\/\\]/, '_')     // Path separators
            .replaceAll(/\.\./, '_')       // Path traversal
            .replaceAll(/^\.+/, '')        // Leading dots
            .replaceAll(/\s+/, '_')        // Multiple spaces
            .replaceAll(/_+/, '_')         // Multiple underscores
            .trim()

        // Ensure filename is not empty and has reasonable length
        if (!sanitized || sanitized.length() > 255) {
            return null
        }

        return sanitized
    }

    /**
     * Checks for path traversal attempts in filename
     */
    static boolean containsPathTraversal(String filename) {
        return PATH_TRAVERSAL_PATTERN.matcher(filename).find()
    }

    /**
     * Extracts file extension from filename
     */
    static String getFileExtension(String filename) {
        if (!filename || !filename.contains('.')) {
            return null
        }

        int lastDot = filename.lastIndexOf('.')
        if (lastDot == filename.length() - 1) {
            return null // Filename ends with dot
        }

        return filename.substring(lastDot + 1).toLowerCase()
    }

    /**
     * Validates file content using magic number signatures
     */
    static boolean validateFileContent(def file, String extension) {
        try {
            byte[] fileHeader = new byte[16] // Read first 16 bytes
            file.inputStream.withCloseable { inputStream ->
                int bytesRead = inputStream.read(fileHeader)
                if (bytesRead <= 0) return false
            }

            // Special handling for text files
            if (extension in ['txt', 'csv', 'css', 'js']) {
                return isTextFile(fileHeader)
            }

            // Excel files are now handled by the standard signature matching below

            List<byte[]> signatures = ALLOWED_SIGNATURES[extension.toLowerCase()]
            if (!signatures) {
                return false // Unknown file type
            }

            return signatures.any { signature ->
                matchesSignature(fileHeader, signature)
            }

        } catch (Exception e) {
            return false
        }
    }

    /**
     * Validates Excel files, including password-protected ones
     */
    static boolean validateExcelFile(def file, String extension, byte[] fileHeader) {
        try {
            List<byte[]> signatures = ALLOWED_SIGNATURES[extension.toLowerCase()]
            if (!signatures) {
                return false
            }

            // First, check if it matches normal Excel signatures
            boolean matchesNormalSignature = signatures.any { signature ->
                matchesSignature(fileHeader, signature)
            }

            if (matchesNormalSignature) {
                return true // Regular Excel file
            }

            // For Excel files that don't match normal signatures, check if they might be password-protected
            if (isPasswordProtectedExcel(file, extension)) {
                return true // Allow password-protected Excel files
            }

            return false // Not a valid Excel file
        } catch (Exception e) {
            return false
        }
    }

    /**
     * Checks if an Excel file is password-protected
     */
    static boolean isPasswordProtectedExcel(def file, String extension) {
        try {
            // Try to detect password-protected Excel files
            if (extension == 'xlsx') {
                return isPasswordProtectedXlsx(file)
            } else if (extension == 'xls') {
                return isPasswordProtectedXls(file)
            }
            return false
        } catch (Exception e) {
            // If we can't determine, assume it might be password-protected and allow it
            return true
        }
    }

    /**
     * Checks if XLSX file is password-protected
     */
    static boolean isPasswordProtectedXlsx(def file) {
        try {
            // Password-protected XLSX files are actually OLE2 containers, not ZIP files
            byte[] ole2Signature = [0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1] as byte[]

            file.inputStream.withCloseable { inputStream ->
                byte[] header = new byte[8]
                int bytesRead = inputStream.read(header)
                if (bytesRead >= 8 && matchesSignature(header, ole2Signature)) {
                    // This is an OLE2 file, which suggests it's password-protected
                    return true
                }
            }
            return false
        } catch (Exception e) {
            return true // If we can't read it, assume it might be password-protected
        }
    }

    /**
     * Checks if XLS file is password-protected
     */
    static boolean isPasswordProtectedXls(def file) {
        try {
            // XLS files are always OLE2, but we need to check deeper to detect encryption
            byte[] ole2Signature = [0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1] as byte[]

            file.inputStream.withCloseable { inputStream ->
                byte[] header = new byte[8]
                int bytesRead = inputStream.read(header)
                if (bytesRead >= 8 && matchesSignature(header, ole2Signature)) {
                    // For XLS files, we'll be more permissive since they're always OLE2
                    // The actual password detection would require parsing the OLE2 structure
                    return true
                }
            }
            return false
        } catch (Exception e) {
            return true // If we can't read it, assume it might be password-protected
        }
    }

    /**
     * Checks if file header matches the expected signature
     */
    static boolean matchesSignature(byte[] fileHeader, byte[] signature) {
        if (fileHeader.length < signature.length) {
            return false
        }

        for (int i = 0; i < signature.length; i++) {
            if (fileHeader[i] != signature[i]) {
                return false
            }
        }

        return true
    }

    /**
     * Checks if file appears to be a text file
     */
    static boolean isTextFile(byte[] header) {
        // Check for common text encodings and printable characters
        for (byte b : header) {
            if (b == 0) return false // Null bytes indicate binary
            if (b < 0x09 || (b > 0x0D && b < 0x20 && b != 0x1A)) {
                return false // Non-printable control characters
            }
        }
        return true
    }

    /**
     * Checks for suspicious patterns in filename
     */
    static boolean containsSuspiciousPatterns(String filename) {
        String lower = filename.toLowerCase()

        // Check for double extensions
        if (lower.matches(/.*\.(exe|bat|cmd|scr|pif)\.(txt|doc|pdf|jpg|png)$/)) {
            return true
        }

        // Check for Unicode/encoding tricks
        if (filename.contains('\u202E') || filename.contains('\u200F')) {
            return true
        }

        return false
    }

    /**
     * Formats file size for human-readable display
     */
    static String formatFileSize(long bytes) {
        if (bytes < 1024) return "${bytes} B"
        if (bytes < 1024 * 1024) return "${Math.round(bytes / 1024.0)} KB"
        if (bytes < 1024 * 1024 * 1024) return "${Math.round(bytes / (1024.0 * 1024.0))} MB"
        return "${Math.round(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }

    /**
     * Creates a secure file path preventing directory traversal
     */
    static String createSecurePath(String baseDir, String filename) {
        try {
            Path basePath = Paths.get(baseDir).normalize().toAbsolutePath()
            Path filePath = basePath.resolve(filename).normalize().toAbsolutePath()

            // Ensure the file path is within the base directory
            if (!filePath.startsWith(basePath)) {
                throw new SecurityException("Path traversal attempt detected")
            }

            return filePath.toString()
        } catch (Exception e) {
            throw new SecurityException("Invalid path: ${e.message}")
        }
    }
}