# PortalTracker Comprehensive Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Core Domain: PortalTracker](#core-domain-portaltracker)
4. [Connected Domains](#connected-domains)
5. [Data Flow and Workflow](#data-flow-and-workflow)
6. [Field Types and Validation](#field-types-and-validation)
7. [Security and Access Control](#security-and-access-control)
8. [Database Schema Management](#database-schema-management)
9. [API Reference](#api-reference)
10. [Examples and Use Cases](#examples-and-use-cases)

---

## Overview

**PortalTracker** is a comprehensive, dynamic data tracking system built on the Grails framework. It provides a flexible infrastructure for creating database-backed forms, workflows, and data management systems without hardcoding database schemas.

### Key Capabilities

- **Dynamic Schema Management**: Create and modify database tables and fields programmatically
- **Flexible Workflow Engine**: Define custom status transitions and approval flows
- **Role-Based Access Control**: Fine-grained permissions at tracker, status, and field levels
- **Data Import/Export**: Excel-based bulk operations with field mapping
- **Audit Trail**: Complete history tracking for Tracker-type systems
- **Email Notifications**: Automated notifications on status changes and transitions
- **Advanced Search**: Full-text search with filtering and parameterized queries
- **Type Safety**: 16 different field types with validation and type conversion

### Three Tracker Types

1. **Tracker**: Full-featured workflow system with audit trails, status management, and role-based transitions
2. **Statement**: Role-based data management without workflow features
3. **DataStore**: Simple data storage without status or role management

---

## Architecture

### Domain Model Hierarchy

```
PortalTracker (Core)
├── PortalTrackerField (Field Definitions)
│   └── PortalTrackerError (Field Validation Rules)
├── PortalTrackerStatus (Workflow States)
│   └── PortalTrackerEmail (Status-based Notifications)
├── PortalTrackerTransition (Status Transitions)
│   ├── PortalTrackerRole (Access Control)
│   └── PortalTrackerEmail (Transition-based Notifications)
├── PortalTrackerFlow (Workflow Flow Definitions)
├── PortalTrackerData (Data Import/Upload Management)
├── PortalTrackerIndex (Database Index Configuration)
└── PortalTrackerEmail (General Email Templates)
```

### Database Table Structure

Each PortalTracker instance creates its own set of database tables:

- **Data Table**: `trak_{module}_{slug}_data` - Stores actual tracker records
- **Audit Table**: `trak_{module}_{slug}_updates` - Stores update history (Tracker type only)

---

## Core Domain: PortalTracker

**File**: `grails-app/domain/g6portal/PortalTracker.groovy`

### Properties

#### Core Identification
- **name** (String): Human-readable name for the tracker
- **slug** (String): URL-friendly unique identifier
- **module** (String): Module/application this tracker belongs to
- **tracker_type** (String): Type - 'Tracker', 'Statement', or 'DataStore'
- **side_menu** (String): Side menu category for navigation

#### Access Control & Permissions
- **allowedroles** (String): Comma-separated list of allowed roles
- **allowadd** (Boolean): Whether users can add new records
- **downloadexcel** (Boolean): Whether Excel download is enabled
- **anonymous_list** (Boolean): Allow anonymous users to view list
- **anonymous_view** (Boolean): Allow anonymous users to view details
- **require_login** (Boolean): Require authentication to access
- **excel_audit** (Boolean): Enable audit trail for Excel operations

#### List & Display Configuration
- **listfields** (String): Comma-separated fields to show in list view
- **hiddenlistfields** (String): Fields to hide in list view
- **excelfields** (String): Fields to include in Excel export
- **filterfields** (String): Fields available for filtering
- **searchfields** (String): Fields searchable via search box
- **defaultsort** (String): Default sorting column
- **defaultlimit** (Integer): Default number of records to show
- **rolesort** (String): Role-based sorting configuration

#### UI & Interaction
- **tickactions** (String): Actions available via checkboxes
- **actionbuttons** (String): Custom action buttons configuration
- **condition_q** (String, TEXT): Additional WHERE condition (validated for security)
- **rowclassval** (String): Groovy expression for row CSS classes

#### Database & Processing
- **datatable** (String): Custom data table name (optional override)
- **trailtable** (String): Custom audit trail table name (optional override)
- **postprocess** (PortalPage): Post-processing page to run after operations

#### Workflow Configuration
- **initial_status** (PortalTrackerStatus): Default status for new records
- **defaultfield** (PortalTrackerField): Default field for display purposes

### Relationships

- **emails** (hasMany): PortalTrackerEmail - Email templates for notifications
- **datas** (hasMany): PortalTrackerData - Data upload/import instances
- **fields** (hasMany): PortalTrackerField - Field definitions (form schema)
- **statuses** (hasMany): PortalTrackerStatus - Workflow status definitions
- **roles** (hasMany): PortalTrackerRole - Role-based access control
- **transitions** (hasMany): PortalTrackerTransition - Status transition rules
- **flows** (hasMany): PortalTrackerFlow - Workflow flow definitions
- **indexes** (hasMany): PortalTrackerIndex - Database index configurations

### Key Methods

#### Database Management

**`updatedb(datasource)`**
- Creates or updates tracker tables and columns
- Creates data table with id and dataupdate_id columns
- Adds record_status column for non-DataStore trackers
- Creates audit trail table for Tracker type
- Delegates to fields for column creation

**`createIndex(datasource)`**
- Creates database indexes on searchfields and filterfields
- Optimizes query performance
- Supports both PostgreSQL and SQL Server

**`fromTable(datasource)`**
- Imports existing table structure as tracker fields
- Auto-detects field types from database column types
- Useful for reverse-engineering existing tables

**`cleardb()`**
- Truncates data and trail tables
- Resets tracker to empty state

#### Data Operations

**`updaterow(datasource, datas)`**
- Inserts or updates a single record
- Uses parameterized queries for SQL injection protection
- Validates field values through PortalTrackerField
- Returns updated data with ID

**`getdatas(id, sql=null)`**
- Retrieves a single record by ID
- Returns map of field names to values

**`rows(qparams=null, order=null, offset=null, limit=null)`**
- Queries multiple records with filtering
- Supports complex query parameters with parseqparams()
- Handles pagination with offset and limit

**`firstRow(qparams=null, qorder=null)`**
- Retrieves first matching record
- Uses parseqparams for flexible querying

**`parseqparams(qparams, joiner='and')`**
- Converts query parameter maps to SQL WHERE clauses
- Supports operators: LIKE (%), <, >, <=, >=, !=, IS NULL
- Supports logical operators: AND, OR, NOT
- Returns query string and parameters for safe execution

#### Workflow & Status Management

**`updatetrail(params, session, request, curuser, datasource, ...)`**
- Creates audit trail entry for Tracker type
- Handles file attachments
- Manages role-based update visibility
- Sends status-based email notifications
- Returns trail entry ID

**`savetrail(record_id, description, curuser, status=null, ...)`**
- Simplified trail creation method
- Wrapper around updatetrail()

**`row_status(datasource, record_id)`**
- Gets current status of a record
- Returns PortalTrackerStatus object

**`newtransition(curuser=null)`**
- Finds transition that creates new records
- Returns transition to initial_status

**`transitionallowed(tname, curuser, datas=null)`**
- Checks if user can execute a transition
- Validates against transition roles

#### Access Control

**`module_roles(curuser=null)`**
- Gets user's roles within this tracker's module
- Returns list of PortalTrackerRole objects

**`user_roles(curuser, datas=null)`**
- Gets all roles applicable to user for specific data
- Includes User Role type and Data Compare type
- Evaluates dynamic role conditions

**`role_query(user)`**
- Generates SQL WHERE conditions for data filtering
- Based on Data Compare type roles

**`checkAdmin(curuser)`**
- Checks if user has Admin role in tracker module

#### Query Building

**`listquery(params, curuser, select="select * ")`**
- Builds complete SQL query for list view
- Applies role-based filtering
- Handles search, filtering, sorting, pagination
- Uses parameterized queries for security
- Returns map with query, params, and query parts

**`updaterecord(params, request, session, sql, defaultfields=[])`**
- Main method for creating/updating records
- Handles all field types and validation
- Manages file uploads with security checks
- Processes default field values
- Handles status transitions and deletions
- Returns map of saved field values

#### Static Utility Methods

**`static load_tracker(slug, module='portal')`**
- Loads tracker by slug and module
- Supports "module:slug" notation

**`static get_value(slug, id, field=null)`**
- Gets field value from tracker record
- Supports "module:slug:field" notation

**`static savedatas(module, slug, datas)`**
- Static method to save data to tracker

**`static getdatas(module, slug, id, sql=null)`**
- Static method to retrieve data from tracker

**`static load_rows(module, slug, qparams=null, order=null)`**
- Static method to query tracker rows

**`static load_datas(module, slug, qparams=null, order=null)`**
- Static method to get first matching row

**`static raw_firstRow(query, params=null)`**
- Execute raw SQL query, return first row

**`static raw_rows(query, params=null)`**
- Execute raw SQL query, return all rows

**`static raw_execute(query, params=null)`**
- Execute raw SQL command

**`static base64Encode(inputString)` / `base64Decode(encodedString)`**
- URL parameter encoding/decoding utilities

**`static encodeparams(params)` / `decodeparams(params)`**
- Encode/decode parameter maps for URL passing

#### Helper Methods

**`data_table()`**
- Returns data table name (custom or generated)

**`trail_table()`**
- Returns audit trail table name (custom or generated)

**`default_field()`**
- Finds default display field (name or title)

**`rowclass(row)`**
- Evaluates rowclassval expression for CSS classes

**`field(name)`**
- Finds PortalTrackerField by name

**`transition(transition_name)`**
- Finds PortalTrackerTransition by name

**`camelcase(dstring)`**
- Converts string to CamelCase

---

## Connected Domains

### 1. PortalTrackerField

**File**: `grails-app/domain/g6portal/PortalTrackerField.groovy`

Defines individual form fields within a tracker system.

#### Properties

- **name** (String): Field name (database column name)
- **label** (String): Human-readable label
- **field_type** (String): Field type - see [Field Types](#field-types)
- **field_options** (String, TEXT): Configuration options (type-specific)
- **field_format** (String, TEXT): Display format expression
- **field_default** (String, TEXT): Default value expression
- **hyperscript** (String, TEXT): HyperScript for client-side behavior
- **field_display** (String): Display configuration
- **field_query** (String, TEXT): Query expression for dynamic options
- **classes** (String): CSS classes
- **hide_heading** (Boolean): Hide field heading
- **params_override** (Boolean): Allow URL params to override value
- **url_value** (Boolean): Include value in URLs
- **is_encrypted** (Boolean): Encrypt field value
- **role_query** (Boolean): Use role-based query filtering
- **encode_exception** (Boolean): HTML encode and convert newlines to <br/>
- **suppress_follow_link** (Boolean): Don't create follow link for BelongsTo fields

#### Relationships

- **tracker** (belongsTo): PortalTracker
- **error_checks** (hasMany): PortalTrackerError

#### Key Methods

**`updatedb(datasource)`**
- Creates or alters database column for this field
- Maps field_type to SQL type
- Creates indexes for relationship fields

**`safeval(value)`**
- Converts value to appropriate type
- Handles Integer, Number, etc.

**`fieldval(value, sql=null)`**
- Formats value for display
- Resolves relationships (User, File, BelongsTo, etc.)
- Handles encryption/decryption
- Processes tracker_objects setting

**`evaloptions(session, datas=null, sql=null)`**
- Evaluates field_options Groovy expression
- Returns options for dropdowns, etc.

**`evalformat(session, datas=null)`**
- Evaluates field_format Groovy expression
- Returns formatted display value

**`evalquery(session, datas)`**
- Evaluates field_query Groovy expression
- Returns query results for dynamic options

**`objectlist(session, params)`**
- Returns list of objects for selection fields
- Supports tracker_objects and BelongsTo fields

**`userlist(session, params)`**
- Returns filtered list of users for User fields

**`nodeslist(session, params)`**
- Returns tree nodes for TreeNode fields

**`othertracker()`**
- Resolves BelongsTo target tracker
- Parses field_options as "module:slug:field"

**`trackerobject()`**
- Returns tracker object configuration from settings

**`fixnamelabel()`**
- Auto-generates name from label if not set

#### Field Types

1. **Text**: VARCHAR(256) - Single-line text
2. **Text Area**: TEXT - Multi-line text
3. **Integer**: NUMERIC(24,0) - Whole numbers
4. **Number**: DECIMAL(24,6) - Decimal numbers
5. **Date**: DATE - Date only
6. **DateTime**: TIMESTAMP/DATETIME - Date and time
7. **Checkbox**: BOOLEAN/BIT - True/false or multi-select checkboxes
8. **Drop Down**: VARCHAR(256) - Select from predefined options
9. **MultiSelect**: VARCHAR(256) - Multiple selections from options
10. **User**: NUMERIC(19,0) - Reference to portal_user table
11. **File**: NUMERIC(19,0) - Reference to FileLink for file uploads
12. **Branch**: NUMERIC(19,0) - Reference to organizational branch
13. **BelongsTo**: NUMERIC(19,0) - Reference to another tracker
14. **HasMany**: Virtual - One-to-many relationship (no column)
15. **TreeNode**: NUMERIC(19,0) - Reference to hierarchical tree structure
16. **Hidden**: VARCHAR(256) - Hidden field
17. **FieldGroup**: Virtual - Grouping of fields (no column)

---

### 2. PortalTrackerStatus

**File**: `grails-app/domain/g6portal/PortalTrackerStatus.groovy`

Defines workflow states for Tracker-type systems.

#### Properties

- **name** (String): Status name (stored in record_status column)
- **displayfields** (String, TEXT): Fields to show in this status
- **editfields** (String, TEXT): Fields that can be edited in this status
- **editroles** (String, TEXT): Roles that can edit in this status
- **updateallowedroles** (String, TEXT): Rules for update visibility (e.g., "Admin->Admin;RD->RD,Admin")
- **updateable** (Boolean): Whether updates/comments can be added
- **attachable** (Boolean): Whether file attachments are allowed
- **suppressupdatebutton** (Boolean): Hide the update button
- **actiontransitions** (Boolean): Show transitions as action buttons
- **flow** (Integer): Flow identifier for workflow visualization
- **emailonupdate** (PortalTrackerEmail): Email sent when update is added

#### Relationships

- **tracker** (belongsTo): PortalTracker

#### Key Methods

**`checkupdateable(user_roles)`**
- Checks if status is updateable for given user roles
- Validates against updateallowedroles rules

**`findpath(target, curpath=[])`**
- Finds path from this status to target status
- Returns list of statuses in path

**`pathtostatus(target)`**
- Finds shortest path to target status
- Returns flattened list of statuses

---

### 3. PortalTrackerTransition

**File**: `grails-app/domain/g6portal/PortalTrackerTransition.groovy`

Defines allowed status transitions and associated forms.

#### Properties

- **name** (String): Transition name
- **display_name** (String): Display name override
- **prev_status** (Set<PortalTrackerStatus>): Starting statuses (empty = new record)
- **next_status** (PortalTrackerStatus): Target status
- **same_status** (Boolean): Whether transition keeps same status
- **roles** (Set<PortalTrackerRole>): Roles allowed to execute transition
- **editfields** (String, TEXT): Fields shown on transition form
- **displayfields** (String, TEXT): Read-only fields shown on form
- **requiredfields** (String, TEXT): Required fields for submission
- **richtextfields** (String, TEXT): Fields to render as rich text editor
- **enabledcondition** (String, TEXT): Groovy expression to enable/disable transition
- **updatetrails** (String, TEXT): Groovy expression for audit trail description
- **submitbuttontext** (String): Custom submit button text
- **cancelbuttontext** (String): Custom cancel button text
- **cancelbutton** (Boolean): Show cancel button
- **immediate_submission** (Boolean): Submit without confirmation
- **postprocess** (PortalPage): Page to run after transition
- **redirect_after** (String): URL to redirect after transition
- **gotoprevstatuslist** (Boolean): Return to previous status list view

#### Relationships

- **tracker** (belongsTo): PortalTracker
- **emails** (hasMany): PortalTrackerEmail - Emails sent on transition

#### Key Methods

**`testenabled(session, datas)`**
- Tests if transition is enabled for current context
- Checks roles and evaluates enabledcondition

**`updatetrail(session, datas, portalService=null)`**
- Evaluates updatetrails expression
- Returns audit trail description

**`sendemails(params, session, sql, ...)`**
- Sends all emails configured for this transition
- Evaluates email recipients and content

---

### 4. PortalTrackerRole

**File**: `grails-app/domain/g6portal/PortalTrackerRole.groovy`

Defines role-based access control rules.

#### Properties

- **name** (String): Role name
- **role_type** (String): "User Role" or "Data Compare"
- **role_rule** (String, TEXT): Groovy expression for Data Compare type
- **role_desc** (String, TEXT): Role description
- **lastUpdated** (Date): Last modification date

#### Relationships

- **tracker** (belongsTo): PortalTracker

#### Role Types

1. **User Role**: Matches user's assigned roles in the module
2. **Data Compare**: Dynamic role based on data comparison (e.g., record creator, department match)

#### Key Methods

**`evalrole(curuser, datas)`**
- Evaluates role_rule GSP template
- Returns SQL WHERE condition for Data Compare roles
- Used in user_roles() and role_query()

---

### 5. PortalTrackerEmail

**File**: `grails-app/domain/g6portal/PortalTrackerEmail.groovy`

Defines email templates for notifications.

#### Properties

- **name** (String): Email template name
- **emailto** (String, TEXT): Groovy expression for recipients
- **emailcc** (String, TEXT): Groovy expression for CC recipients
- **body** (PortalPage): Email content (title and body)

#### Relationships

- **tracker** (belongsTo): PortalTracker (optional)
- **transition** (belongsTo): PortalTrackerTransition (optional)
- **status** (belongsTo): PortalTrackerStatus (optional)

#### Key Methods

**`evalbody(datas, groovyPagesTemplateEngine, portalService=null)`**
- Evaluates GSP template for email body and title
- Returns map with 'title' and 'body'
- Available variables: datas, portalService

---

### 6. PortalTrackerData

**File**: `grails-app/domain/g6portal/PortalTrackerData.groovy`

Manages Excel data imports and bulk uploads.

#### Properties

- **path** (String): File path to uploaded Excel
- **date_created** (Date): Upload date
- **data_row** (Integer): Starting row for data
- **data_end** (Integer): Ending row for data
- **header_start** (Integer): Header start row
- **header_end** (Integer): Header end row
- **uploaded** (Boolean): Upload completed flag
- **send_email** (Boolean): Send notification email
- **sent_email_date** (Date): Email sent date
- **messages** (String, TEXT): Upload status messages
- **excel_password** (String): Password for encrypted Excel files
- **uploader** (User): User who uploaded
- **savedparams** (String, TEXT): JSON field mapping configuration
- **uploadStatus** (Integer): Upload status code (-1=processing, 1=complete)
- **file_link** (FileLink): Reference to uploaded file
- **isTrackerDeleting** (Boolean): Internal flag for cascade delete

#### Relationships

- **tracker** (belongsTo): PortalTracker
- **module** (String): Module name

#### Key Methods

**`update(mailService)`**
- Processes the Excel upload
- Maps columns to fields based on savedparams
- Inserts/updates data using PoiExcel
- Sets initial_status for new records
- Executes postprocess if configured

**`fromFileLink(manualmaps=null, otherparams=null, update_fields=null, ignore_fields=null, autosearch=true)`**
- Configures upload from a FileLink
- Auto-detects column headers
- Maps headers to fields using fuzzy matching (Longest Common Subsequence)
- Supports manual mapping overrides
- Configures which fields are update keys

**`beforeDelete()`**
- Cascade deletes uploaded data records
- Deletes uploaded file from filesystem

---

### 7. PortalTrackerFlow

**File**: `grails-app/domain/g6portal/PortalTrackerFlow.groovy`

Defines workflow flow configurations for visualization.

#### Properties

- **name** (String): Flow name
- **fields** (String, TEXT): Fields in this flow
- **transitions** (String, TEXT): Transitions in this flow

#### Relationships

- **tracker** (belongsTo): PortalTracker

---

### 8. PortalTrackerError

**File**: `grails-app/domain/g6portal/PortalTrackerError.groovy`

Defines field validation rules.

#### Properties

- **description** (String): Error description
- **error_type** (String): Validation type - 'Unique', 'E-mail', 'Format', 'Not Empty', 'Custom'
- **format** (String): Format pattern (for Format type)
- **error_msg** (String, TEXT): Error message to display
- **error_function** (String, TEXT): Custom validation function (for Custom type)
- **allow_submission** (Boolean): Allow submission despite error (warning only)

#### Relationships

- **field** (belongsTo): PortalTrackerField

---

### 9. PortalTrackerIndex

**File**: `grails-app/domain/g6portal/PortalTrackerIndex.groovy`

Manages custom database indexes for performance.

#### Properties

- **name** (String): Index name
- **fields** (String, TEXT): Comma-separated field names

#### Relationships

- **tracker** (belongsTo): PortalTracker

#### Key Methods

**`updateDb()`**
- Creates or updates database index
- Supports composite indexes
- Platform-specific syntax (PostgreSQL vs SQL Server)

**`deleteIndex()`**
- Drops the database index

---

## Data Flow and Workflow

### Creating a New Tracker Record

1. **User selects transition** for new record (prev_status is empty)
2. **System validates** user has role allowed for transition
3. **Form displays** with editfields and displayfields from transition
4. **User fills form** and submits
5. **System validates** required fields and error checks
6. **updaterecord()** creates draft record (if Tracker type) or new record (if DataStore)
7. **updaterecord()** processes each field:
   - Validates field type
   - Applies field defaults
   - Handles file uploads with security checks
   - Converts data types
8. **updatetrail()** creates audit entry (if Tracker type)
9. **Transition emails** are sent to configured recipients
10. **Postprocess** page executes if configured
11. **Redirect** to configured page or tracker list

### Updating an Existing Record

1. **User selects transition** from current status
2. **testenabled()** validates role and enabledcondition
3. **Form displays** with transition configuration
4. **User modifies** editfields
5. **updaterecord()** updates record fields
6. **updatetrail()** adds audit entry with description
7. **Status emails** sent if emailonupdate configured
8. **Postprocess** and **redirect** as configured

### Excel Data Import

1. **User uploads** Excel file via PortalTrackerData
2. **fromFileLink()** auto-detects headers and maps to fields
3. **User reviews** and adjusts field mappings
4. **savedparams** stored as JSON mapping configuration
5. **update()** processes file:
   - PoiExcel reads rows
   - For each row, creates/updates record via updaterow()
   - Applies initial_status to new records
   - Executes postprocess if configured
6. **Upload status** updated with row count
7. **Email notifications** sent if configured

### Role-Based Data Filtering

1. **User accesses** tracker list view
2. **listquery()** builds SQL query
3. **user_roles()** determines user's roles:
   - User Role: Checks UserRole assignments
   - Data Compare: Evaluates role_rule against data
4. **role_query()** generates WHERE conditions
5. **Query executes** with role-based filtering
6. **Results displayed** with only accessible records

---

## Security and Access Control

### Multi-Level Security

1. **Module Level**: User must have role in tracker's module
2. **Tracker Level**: allowedroles controls overall access
3. **Role Level**: PortalTrackerRole defines fine-grained access
4. **Status Level**: editroles controls editing per status
5. **Transition Level**: roles controls who can execute transition
6. **Field Level**: editfields controls field editability
7. **Data Level**: Data Compare roles filter by record content

### SQL Injection Protection

- All queries use **parameterized queries** with named parameters
- User input never concatenated directly into SQL
- parseqparams() safely converts filters to SQL
- condition_q validated (should use SecurityValidator)
- Field names validated against PortalTrackerField definitions

### File Upload Security

File uploads in PortalTrackerField (lines 1531-1634) include comprehensive security:

1. **File Extension Whitelist**: Only allowed extensions (jpg, jpeg, png, gif, pdf, doc, docx, xls, xlsx, txt, zip)
2. **File Size Limit**: 10MB default maximum
3. **Empty File Check**: Rejects zero-byte files
4. **Filename Sanitization**:
   - Removes path separators (/, \)
   - Allows only alphanumeric, dots, underscores, hyphens
   - Removes multiple consecutive dots
   - Prevents dot-prefixed hidden files
5. **Path Traversal Prevention**: Sanitized filenames prevent directory escape
6. **Duplicate Handling**: Auto-renames if file exists
7. **Audit Logging**: Failed uploads logged to PortalErrorLog

### Encryption Support

- Fields with is_encrypted=true use CryptoUtilsFile
- Encryption key from config.encryption.key
- Transparent encryption/decryption in fieldval() and updaterecord()

---

## Database Schema Management

### Dynamic Schema Creation

Each tracker automatically creates:

```sql
-- Data Table
CREATE TABLE trak_{module}_{slug}_data (
    id SERIAL PRIMARY KEY,
    dataupdate_id NUMERIC(19,0) NULL,
    record_status VARCHAR(255) NULL,  -- Tracker/Statement types only
    -- Dynamic fields added by PortalTrackerField.updatedb()
)

-- Audit Trail Table (Tracker type only)
CREATE TABLE trak_{module}_{slug}_updates (
    id SERIAL PRIMARY KEY,
    attachment_id NUMERIC(19,0),
    description TEXT,
    record_id NUMERIC(19,0),
    update_date TIMESTAMP,
    updater_id NUMERIC(19,0),
    status VARCHAR(255),
    changes TEXT,
    allowedroles VARCHAR(255)
)
```

### Field Type to SQL Type Mapping

| Field Type   | PostgreSQL           | SQL Server          |
|--------------|----------------------|---------------------|
| Text         | VARCHAR(256)         | VARCHAR(256)        |
| Text Area    | TEXT                 | TEXT                |
| Integer      | NUMERIC(24,0)        | NUMERIC(24,0)       |
| Number       | DECIMAL(24,6)        | DECIMAL(24,6)       |
| Date         | DATE                 | DATE                |
| DateTime     | TIMESTAMP            | DATETIME            |
| Checkbox     | BOOLEAN              | BIT                 |
| User         | NUMERIC(19,0)        | NUMERIC(19,0)       |
| File         | NUMERIC(19,0)        | NUMERIC(19,0)       |
| Branch       | NUMERIC(19,0)        | NUMERIC(19,0)       |
| BelongsTo    | NUMERIC(19,0)        | NUMERIC(19,0)       |
| TreeNode     | NUMERIC(19,0)        | NUMERIC(19,0)       |
| Drop Down    | VARCHAR(256)         | VARCHAR(256)        |
| MultiSelect  | VARCHAR(256)         | VARCHAR(256)        |
| Hidden       | VARCHAR(256)         | VARCHAR(256)        |

### Index Management

Automatic index creation on:
- searchfields
- filterfields
- Relationship fields (BelongsTo, User, File, Branch, TreeNode)
- Custom indexes via PortalTrackerIndex

---

## API Reference

### Static Methods

```groovy
// Load tracker
def tracker = PortalTracker.load_tracker('module:slug')

// Get field value
def value = PortalTracker.get_value('module:slug:field', recordId)

// Query records
def rows = PortalTracker.load_rows('module', 'slug',
    ['field1': 'value1', 'field2': '%search%'],
    'field1 desc')

// Get single record
def data = PortalTracker.load_datas('module', 'slug',
    ['id': recordId])

// Save data
PortalTracker.savedatas('module', 'slug',
    ['field1': 'value1', 'field2': 123])

// Raw SQL
def result = PortalTracker.raw_firstRow(
    'SELECT * FROM table WHERE id=:id',
    ['id': 123])
```

### Instance Methods

```groovy
// Get tracker field
def field = tracker.field('fieldname')

// Get transition
def transition = tracker.transition('transitionname')

// Save record
def savedData = tracker.savedatas(['field1': 'value1'])

// Get record
def record = tracker.getdatas(recordId)

// Query records
def results = tracker.rows(['field1': 'value'], 'field1', 0, 10)

// Update database schema
tracker.updatedb(datasource)

// Create indexes
tracker.createIndex(datasource)

// Check user access
def roles = tracker.user_roles(user, ['id': recordId])
def canTransition = tracker.transitionallowed('transitionname', user, data)
```

### Query Parameters

```groovy
// Exact match
['field': 'value']

// LIKE search
['field': '%search%']

// Comparison operators
['field': '>100']
['field': '<=50']
['field': '!exclude']

// Array (OR conditions)
['field': ['value1', 'value2']]

// Date range
['datefield': 'between 2024-01-01_2024-12-31']

// Complex queries
[
    'and': [
        'field1': 'value1',
        'field2': '%search%'
    ],
    'or': [
        'field3': '>100',
        'field4': ['opt1', 'opt2']
    ]
]

// NULL checks
['field': null]

// Nested NOT
['not': ['field': 'value']]
```

---

## Examples and Use Cases

### Example 1: Document Approval Workflow

```groovy
// Tracker Configuration
name: "Document Approval"
slug: "doc_approval"
module: "documents"
tracker_type: "Tracker"

// Fields
- title (Text)
- description (Text Area)
- document (File)
- department (Drop Down)
- submitter (User)
- submit_date (Date)

// Statuses
- Draft
- Pending Review
- Approved
- Rejected

// Transitions
1. "Submit for Review": Draft -> Pending Review
   - Roles: Submitter
   - Email: Notify reviewers

2. "Approve": Pending Review -> Approved
   - Roles: Manager
   - Email: Notify submitter

3. "Reject": Pending Review -> Rejected
   - Roles: Manager
   - Email: Notify submitter with reason

4. "Resubmit": Rejected -> Pending Review
   - Roles: Submitter

// Roles
- Submitter (Data Compare): submitter=session.userid
- Manager (User Role): Department Managers
```

### Example 2: Simple Data Collection (DataStore)

```groovy
// Tracker Configuration
name: "Customer Feedback"
slug: "feedback"
module: "crm"
tracker_type: "DataStore"
anonymous_list: false
anonymous_view: false

// Fields
- customer_name (Text, required)
- email (Text, validation: E-mail)
- feedback (Text Area, required)
- rating (Drop Down, options: 1-5)
- submit_date (DateTime, default: new Date())

// No workflow - direct data entry
```

### Example 3: Statement with Role-Based Filtering

```groovy
// Tracker Configuration
name: "Sales Reports"
slug: "sales_reports"
module: "sales"
tracker_type: "Statement"

// Fields
- report_date (Date)
- salesperson (User)
- region (Drop Down)
- revenue (Number)
- notes (Text Area)

// Roles
- Regional Manager (Data Compare):
  role_rule: "region='${curuser.region}'"

- Sales Person (Data Compare):
  role_rule: "salesperson=${curuser.id}"

- National Manager (User Role):
  Sees all records
```

### Example 4: Bulk Import with Excel

```groovy
// Create PortalTrackerData
def upload = new PortalTrackerData(
    tracker: myTracker,
    file_link: uploadedFile,
    uploader: currentUser
)

// Auto-map columns
upload.fromFileLink(
    manualmaps: ['Full Name': 'name'],  // Manual overrides
    update_fields: ['email'],           // Use email as update key
    ignore_fields: [idField],           // Don't import ID
    autosearch: true                    // Auto-detect other columns
)

// Process upload
upload.update(mailService)
```

### Example 5: Dynamic Field Options

```groovy
// Field: department
field_type: "Drop Down"
field_options: """
Department.findAll {
    active == true
    if(session.userDepartment) {
        parent == session.userDepartment
    }
}.sort { it.name }
"""

// Field: manager
field_type: "User"
field_options: """
User.findAll {
    isActive == true
    'Manager' in roles*.authority
    department == datas?.department
}
"""
```

### Example 6: Conditional Transitions

```groovy
// Transition: "Fast Track Approval"
enabledcondition: """
datas.amount < 1000 &&
datas.submitter.department == session.curuser.department
"""

// Only enabled for small amounts in same department
```

### Example 7: Custom Row Styling

```groovy
// Tracker configuration
rowclassval: """
if(row.status == 'Overdue') {
    return 'bg-danger text-white'
} else if(row.priority == 'High') {
    return 'bg-warning'
} else {
    return ''
}
"""
```

---

## Best Practices

### 1. Tracker Design

- **Choose the right type**:
  - Tracker for workflows with approvals
  - Statement for role-based data with simple CRUD
  - DataStore for simple data collection

- **Define clear statuses**: Each status should represent a distinct business state

- **Keep transitions simple**: Each transition should do one logical thing

- **Use meaningful field names**: Use underscores, not spaces (auto-generated from labels)

### 2. Performance

- **Define searchfields and filterfields**: Only include fields users actually search/filter

- **Use PortalTrackerIndex**: For complex multi-field queries

- **Set reasonable defaultlimit**: Prevent loading thousands of records

- **Use BelongsTo carefully**: Can slow down large lists

### 3. Security

- **Always define allowedroles**: Don't rely on anonymous_list=false alone

- **Use Data Compare roles**: For row-level security (e.g., "see only your department")

- **Validate file uploads**: Default security is good, but review for your use case

- **Test transition permissions**: Ensure users can't skip approval steps

- **Encrypt sensitive fields**: Set is_encrypted=true for PII

### 4. User Experience

- **Clear transition names**: Use display_name for user-friendly labels

- **Helpful error messages**: Define PortalTrackerError with clear error_msg

- **Logical field grouping**: Use displayfields and editfields to control layout

- **Progress indicators**: Use flows to show workflow progress

- **Email notifications**: Keep users informed of status changes

### 5. Maintenance

- **Document custom field_options**: Complex Groovy expressions need comments

- **Test postprocess pages**: These run automatically and can break workflows

- **Monitor PortalErrorLog**: Check for validation and processing errors

- **Version control tracker configs**: Export and track changes

- **Regular index maintenance**: Run createIndex() after adding search fields

---

## Troubleshooting

### Common Issues

**Issue**: Records not appearing in list
- Check user_roles() - may not have required role
- Check condition_q - may be filtering records
- Check role_query() - Data Compare rules may be too restrictive

**Issue**: Transition not available
- Check prev_status - must match current record status
- Check testenabled() - roles or enabledcondition may be blocking
- Check tracker type - DataStore doesn't support transitions

**Issue**: Field not saving
- Check field name - must match PortalTrackerField.name
- Check field_type - value must match type
- Check validation - PortalTrackerError may be blocking
- Check updaterecord() - may be setting validfield=false

**Issue**: Email not sending
- Check MailService configuration
- Check evalbody() - GSP template may have errors
- Check emailto expression - must return valid email string
- Check PortalErrorLog for mail errors

**Issue**: Excel import failing
- Check file format - must be valid Excel (.xls, .xlsx)
- Check column headers - must match or be mappable to fields
- Check field types - data must be compatible
- Check PoiExcel processing in PortalTrackerData.update()

**Issue**: Slow performance
- Add indexes via createIndex() or PortalTrackerIndex
- Reduce number of records displayed with defaultlimit
- Optimize searchfields and filterfields
- Review complex field_options expressions

---

## Advanced Topics

### Custom Query Building

The parseqparams() method supports complex queries:

```groovy
// Complex nested query
def params = [
    'and': [
        'status': ['Active', 'Pending'],
        'amount': '>1000'
    ],
    'or': [
        'department': 'Sales',
        'priority': 'High'
    ],
    'not': [
        'archived': '1'
    ]
]

def result = tracker.parseqparams(params)
// result.query: "((status=:status_1 or status=:status_2) and amount > :amount) and (department=:department or priority=:priority) and  not (archived=:archived)"
// result.params: [status_1:'Active', status_2:'Pending', amount:'1000', department:'Sales', priority:'High', archived:'1']
```

### Dynamic Tracker Objects

Configure custom objects via PortalSetting "tracker_objects":

```groovy
tracker_objects: [
    'Project': 'projects.project.name',
    'Customer': 'crm.customer.company_name'
]

// Then use as field_type
field_type: 'Project'  // Creates NUMERIC(19,0) referencing projects tracker
```

### Audit Trail Visibility Control

Control who sees updates with updateallowedroles:

```groovy
// Status configuration
updateallowedroles: "Admin->Admin;Manager->Manager,Admin;User->all"

// Means:
// - Updates by Admin visible only to Admin
// - Updates by Manager visible to Manager and Admin
// - Updates by User visible to everyone
```

### GSP Template Variables

In field_options, field_format, field_default, role_rule, etc.:

```groovy
Available variables:
- session: HTTP session
- curuser: Current User object (session.curuser)
- datas: Current record data map
- sql: SQL connection
- portalService: Portal service bean
- params: Request parameters (in some contexts)
```

### Programmatic Access

```groovy
// In a controller or service
def tracker = PortalTracker.load_tracker('module:slug')

// Create new record
def newData = tracker.savedatas([
    'title': 'New Record',
    'amount': 1500.00,
    'created_by': session.userid
])

// Query records
def pending = tracker.rows(
    ['status': 'Pending', 'amount': '>1000'],
    'created_date desc',
    0,
    50
)

// Execute transition
def transition = tracker.transition('approve')
if(tracker.transitionallowed('approve', currentUser, dataRecord)) {
    // Update and transition
    tracker.updaterecord(params, request, session, sql)
    tracker.updatetrail(params, session, request, currentUser, sql)
    transition.sendemails(params, session, sql, templateEngine, portalService)
}
```

---

## Appendix A: Database Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                       PortalTracker                          │
├─────────────────────────────────────────────────────────────┤
│ id, name, slug, module, tracker_type                        │
│ allowedroles, allowadd, downloadexcel                        │
│ listfields, searchfields, filterfields                       │
│ initial_status_id, defaultfield_id, postprocess_id          │
└──────────┬──────────────────────────────────────────────────┘
           │
           ├──────────┐
           │          │
           ▼          ▼
    ┌──────────┐ ┌──────────────────┐
    │  Field   │ │     Status       │
    ├──────────┤ ├──────────────────┤
    │ name     │ │ name             │
    │ type     │ │ displayfields    │
    │ options  │ │ editfields       │
    │ format   │ │ updateable       │
    └────┬─────┘ └────┬─────────────┘
         │            │
         ▼            ▼
    ┌────────┐   ┌──────────────┐
    │ Error  │   │ Transition   │
    ├────────┤   ├──────────────┤
    │ type   │   │ name         │
    │ msg    │   │ prev_status  │
    └────────┘   │ next_status  │
                 │ editfields   │
                 └──────┬───────┘
                        │
                        ▼
                   ┌─────────┐
                   │  Email  │
                   ├─────────┤
                   │ emailto │
                   │ body_id │
                   └─────────┘
```

---

## Appendix B: Workflow State Machine Example

```
        [New Record]
             │
             │ (Submit)
             ▼
         [Draft]
             │
             │ (Request Review)
             ▼
      [Pending Review] ←──────┐
         │       │            │
(Approve)│       │(Reject)    │
         │       │            │
         ▼       ▼            │
    [Approved] [Rejected]     │
                   │          │
                   │(Resubmit)│
                   └──────────┘
```

---

## Appendix C: File Structure Reference

```
g6portal/
├── grails-app/
│   ├── domain/g6portal/
│   │   ├── PortalTracker.groovy           (Core tracker domain)
│   │   ├── PortalTrackerField.groovy      (Field definitions)
│   │   ├── PortalTrackerStatus.groovy     (Workflow statuses)
│   │   ├── PortalTrackerTransition.groovy (Status transitions)
│   │   ├── PortalTrackerRole.groovy       (Access control roles)
│   │   ├── PortalTrackerEmail.groovy      (Email templates)
│   │   ├── PortalTrackerData.groovy       (Excel import)
│   │   ├── PortalTrackerFlow.groovy       (Workflow flows)
│   │   ├── PortalTrackerError.groovy      (Field validation)
│   │   └── PortalTrackerIndex.groovy      (Database indexes)
│   │
│   ├── controllers/g6portal/
│   │   ├── PortalTrackerController.groovy
│   │   ├── PortalTrackerDataController.groovy
│   │   ├── PortalTrackerFieldController.groovy
│   │   └── ... (11 controllers total)
│   │
│   ├── services/g6portal/
│   │   ├── PortalTrackerService.groovy
│   │   ├── PortalTrackerFlowService.groovy
│   │   └── ... (11 services total)
│   │
│   ├── views/
│   │   ├── portalTracker/
│   │   ├── portalTrackerData/
│   │   ├── portalTrackerField/
│   │   └── ... (10 view directories, 48 GSP files total)
│   │
│   └── taglib/g6portal/
│       ├── TrackerTagLib.groovy
│       ├── TreeTagLib.groovy
│       └── PortalTagLib.groovy
│
├── src/test/groovy/g6portal/
│   ├── PortalTrackerFlowControllerSpec.groovy
│   ├── PortalTrackerFlowServiceSpec.groovy
│   └── PortalTrackerFlowSpec.groovy
│
└── docs/
    ├── portaltracker-quick-reference.html
    ├── portaltracker-comprehensive-guide.html
    └── PORTALTRACKER_DOCUMENTATION.md (this file)
```

---

## Glossary

**Tracker**: A PortalTracker instance configured for full workflow with statuses, transitions, and audit trails

**Statement**: A PortalTracker instance configured for simple CRUD with role-based filtering, no workflow

**DataStore**: A PortalTracker instance configured for simple data storage, no workflow or roles

**Slug**: URL-friendly unique identifier for a tracker (e.g., "customer_feedback")

**Module**: Organizational grouping for trackers (e.g., "crm", "hr", "projects")

**Field Type**: The data type and UI widget for a tracker field (16 types available)

**Status**: A state in a workflow (e.g., Draft, Pending Review, Approved)

**Transition**: An allowed movement from one status to another, with associated form and permissions

**Role**: A permission set that controls access to tracker, transitions, or data

**User Role**: A role assigned to users in the module

**Data Compare**: A dynamic role based on comparing data values (e.g., record creator)

**Audit Trail**: History of updates and status changes (trak_*_updates table)

**Trail Entry**: A single record in the audit trail

**BelongsTo**: A field type that references another tracker (foreign key relationship)

**HasMany**: A field type that represents a one-to-many relationship (virtual, no column)

**Data Table**: The database table storing tracker records (trak_*_data)

**Field Options**: Configuration for a field, often a Groovy expression returning valid values

**Field Format**: Expression defining how to display a field value

**Field Default**: Expression providing default value for a field

**Enabled Condition**: Expression determining if a transition is available

**Update Trails**: Expression generating audit trail description for a transition

**Post Process**: Code to execute after a transition or data import

---

## Conclusion

PortalTracker provides a complete framework for building dynamic data management systems. By understanding the core concepts and connected domains, you can create sophisticated workflows, data collection forms, and approval processes without writing database-specific code.

For questions or support, please refer to the codebase at `/home/user/g6portal/grails-app/domain/g6portal/` or consult the development team.

**Version**: 1.0
**Last Updated**: 2025-11-05
**Author**: Documentation generated from codebase analysis
