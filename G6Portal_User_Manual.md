# G6Portal User Manual
## A Comprehensive Guide for Portal Module Management

### Table of Contents
1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [Understanding Portal Components](#understanding-portal-components)
4. [Module Management](#module-management)
5. [Creating and Managing Trackers](#creating-and-managing-trackers)
6. [Page Management](#page-management)
7. [User and Role Management](#user-and-role-management)
8. [File Management](#file-management)
9. [Advanced Features](#advanced-features)
10. [Troubleshooting](#troubleshooting)

---

## Introduction

G6Portal is a comprehensive web-based portal system built on Grails 6.2.2 framework. It provides a flexible platform for creating dynamic modules, workflows, content management, and user role-based access control. This manual is designed to help new staff understand how to manage and create new modules within the portal system.

### Key Features
- **Modular Architecture**: Organize content and functionality into discrete modules
- **Dynamic Workflow System**: Create complex workflows with approval processes
- **Content Management**: Build dynamic pages with embedded code execution
- **Role-Based Security**: Granular permission control at module and component levels
- **File Management**: Integrated file upload, storage, and access control
- **User Management**: LDAP integration and hierarchical role assignment

---

## Getting Started

### System Access
1. **URL**: Access the portal at `http://your-server:9090`
2. **Authentication**: Login using your credentials or Google OAuth (if configured)
3. **Initial Setup**: If this is a new installation, use the setup wizard to configure basic settings

### Navigation Overview
- **Dashboard**: Main landing page with module overview
- **Modules**: Access to all available portal modules
- **Admin Tools**: System administration features (admin users only)
- **Profile**: Personal settings and profile management

---

## Understanding Portal Components

### 1. Portal Modules
**What is a Module?**
A module is a self-contained organizational unit that groups related functionality together. Each module can contain:
- Trackers (workflow systems)
- Pages (content management)
- Settings (configuration)
- User roles (access control)
- Files (document management)

**Module Types:**
- **Business Modules**: Specific business processes (HR, Finance, etc.)
- **Administrative Modules**: System management and configuration
- **Content Modules**: Information sharing and documentation

### 2. Portal Trackers
**What is a Tracker?**
A tracker is the core workflow component that allows you to create dynamic forms, manage data, and implement approval processes.

**Tracker Types:**
- **Tracker**: Full workflow with status tracking and transitions
- **Statement**: Simple role-based data entry without workflow
- **DataStore**: Basic data storage without approval processes

### 3. Portal Pages
**What is a Page?**
Pages are dynamic content areas that can display information, forms, and interactive elements using Groovy Server Pages (GSP) templates.

---

## Module Management

### Creating a New Module

1. **Navigate to Module Management**
   - Go to **Admin Tools** → **Portal Modules**
   - Click **Create New Module**

2. **Configure Module Properties**
   ```
   Module Name: [Enter descriptive name]
   Description: [Brief description of module purpose]
   ```

3. **Set Module Permissions**
   - Define which user roles can access the module
   - Set up module-specific administrators

### Module Import/Export

**Exporting a Module:**
1. Go to **Portal Modules** → **[Module Name]**
2. Click **Export Module**
3. Choose export options:
   - Include Settings
   - Include Pages
   - Include Trackers
   - Include User Roles
   - Include Files

**Importing a Module:**
1. Go to **Portal Modules** → **Import Module**
2. Upload the exported module file
3. Review import options and conflicts
4. Complete the import process

---

## Creating and Managing Trackers

### Step 1: Create a New Tracker

1. **Access Tracker Management**
   - Navigate to **Trackers** → **Create New Tracker**

2. **Basic Tracker Configuration**
   ```
   Tracker Name: [Descriptive name]
   Description: [Purpose and usage]
   Module: [Select appropriate module]
   Tracker Type: [Tracker/Statement/DataStore]
   ```

3. **Advanced Settings**
   - **Allow Multiple Entries**: Can users create multiple records?
   - **Paginate Results**: Enable pagination for large datasets
   - **Enable Search**: Allow users to search records
   - **Enable Indexing**: Improve search performance

### Step 2: Define Tracker Fields

**Field Types Available:**
- **Text**: Single-line text input
- **Textarea**: Multi-line text input
- **Date**: Date picker
- **User**: User selection dropdown
- **File**: File upload
- **BelongsTo**: Relationship to another tracker
- **Checkbox**: Boolean true/false
- **Dropdown**: Predefined options
- **Number**: Numeric input
- **Email**: Email address validation

**Adding Fields:**
1. Go to **Tracker** → **Fields** → **Add Field**
2. Configure field properties:
   ```
   Field Name: [Internal name]
   Display Name: [User-friendly label]
   Field Type: [Select from available types]
   Required: [Yes/No]
   Default Value: [Optional]
   Validation Rules: [Optional]
   ```

### Step 3: Configure Workflow (For Tracker Type)

**Creating Status Definitions:**
1. Go to **Tracker** → **Statuses** → **Add Status**
2. Configure status properties:
   ```
   Status Name: [e.g., "Draft", "Pending Review", "Approved"]
   Description: [Status purpose]
   Color: [Visual indicator]
   Email Notifications: [Enable/Disable]
   ```

**Setting Up Transitions:**
1. Go to **Tracker** → **Transitions** → **Add Transition**
2. Define transition rules:
   ```
   From Status: [Starting status]
   To Status: [Ending status]
   Allowed Roles: [Who can perform this transition]
   Email Notifications: [Notify on transition]
   ```

### Step 4: Configure Roles and Permissions

**Role-Based Access Control:**
1. Go to **Tracker** → **Roles** → **Add Role**
2. Define role permissions:
   ```
   Role Name: [e.g., "Creator", "Approver", "Viewer"]
   Permissions:
   - Create Records: [Yes/No]
   - Edit Records: [Yes/No]
   - Delete Records: [Yes/No]
   - View Records: [Yes/No]
   - Execute Transitions: [Yes/No]
   ```

**Field-Level Security:**
- Set field visibility by status
- Define which roles can edit specific fields
- Configure read-only fields for certain statuses

---

## Page Management

### Creating Dynamic Pages

1. **Create New Page**
   - Go to **Pages** → **Create New Page**
   - Configure basic properties:
     ```
     Page Name: [Internal reference]
     Title: [Display title]
     Module: [Select module]
     ```

2. **Page Security**
   - **Login Required**: Require user authentication
   - **Allowed Roles**: Specify who can access the page
   - **Module Roles**: Use module-specific roles

3. **Content Development**
   - Use GSP (Groovy Server Pages) syntax
   - Embed Groovy code for dynamic content
   - Access database through SQL queries

### Page Data Sources

**Adding Data Sources:**
1. Go to **Page** → **Data Sources** → **Add Data Source**
2. Configure data source:
   ```
   Data Source Name: [Reference name]
   SQL Query: [Database query]
   Parameters: [Optional query parameters]
   ```

**Using Data Sources in Pages:**
```groovy
<g:each in="${dataSourceName}" var="record">
    <div>${record.columnName}</div>
</g:each>
```

### Page Output Formats

**Available Formats:**
- **HTML**: Standard web page
- **XML**: XML document output
- **JSON**: JSON data format
- **File**: File download
- **XLSX**: Excel spreadsheet

---

## User and Role Management

### User Administration

**Creating Users:**
1. Go to **Admin Tools** → **Users** → **Create User**
2. Configure user properties:
   ```
   Username: [Login identifier]
   Email: [Email address]
   Full Name: [Display name]
   Password: [Initial password]
   ```

**User Roles:**
- **Admin**: Full system access
- **Module Admin**: Module-specific administration
- **Regular User**: Standard portal access

### Role Assignment

**Module-Level Roles:**
1. Go to **Module** → **User Roles** → **Add Role**
2. Assign users to module roles:
   ```
   User: [Select user]
   Role: [Select role]
   Module: [Target module]
   ```

**Tracker-Level Roles:**
- Assign users to specific tracker roles
- Define workflow permissions
- Set data access levels

---

## File Management

### File Upload and Storage

**File Upload Process:**
1. Files are uploaded through tracker fields or direct upload
2. System automatically handles file storage and security
3. Files are linked to user accounts and permissions

**File Access Control:**
- **Public Files**: Accessible to all users
- **Role-Based Access**: Restricted to specific roles
- **Owner-Only**: Accessible only to the uploader

### File Organization

**File Categories:**
- **Tracker Attachments**: Files linked to specific tracker records
- **Profile Pictures**: User avatar images
- **Module Resources**: Module-specific files
- **System Files**: Administrative and configuration files

---

## Advanced Features

### Email Integration

**Email Configuration:**
1. Configure SMTP settings in application.yml
2. Set up email templates for notifications
3. Configure tracker-based email triggers

**Email Notifications:**
- Status change notifications
- Transition alerts
- Custom email triggers

### Search and Indexing

**Search Configuration:**
1. Enable search on trackers and pages
2. Configure search indexes
3. Set up search permissions

**Search Features:**
- Full-text search across records
- Field-specific filters
- Advanced search operators

### Scheduler Integration

**Scheduled Tasks:**
1. Go to **Admin Tools** → **Scheduler**
2. Create scheduled jobs:
   ```
   Job Name: [Descriptive name]
   Cron Expression: [Schedule definition]
   Job Type: [Select job type]
   ```

**Common Scheduled Tasks:**
- Data synchronization
- Report generation
- Cleanup operations
- Email notifications

---

## Troubleshooting

### Common Issues

**Login Problems:**
1. Check user credentials
2. Verify LDAP configuration
3. Check user account status

**Permission Errors:**
1. Verify user roles and permissions
2. Check module access rights
3. Confirm tracker-level permissions

**Performance Issues:**
1. Review database queries
2. Check cache configuration
3. Monitor system resources

### Error Logging

**Error Tracking:**
1. Go to **Admin Tools** → **Error Logs**
2. Review system errors and exceptions
3. Monitor user activity logs

**Audit Trail:**
- All user actions are logged
- Track data changes and access
- Monitor system usage patterns

### System Maintenance

**Regular Maintenance Tasks:**
1. Review and clean up old files
2. Monitor database performance
3. Update user permissions
4. Backup modules and data

**Database Maintenance:**
- Regular backup procedures
- Index optimization
- Data archival strategies

---

## Best Practices

### Module Design
1. **Plan Before Creating**: Design your module structure before implementation
2. **Use Clear Naming**: Choose descriptive names for all components
3. **Document Everything**: Maintain clear documentation for complex workflows
4. **Test Thoroughly**: Test all user scenarios before deployment

### Security Considerations
1. **Principle of Least Privilege**: Grant minimum required permissions
2. **Regular Access Reviews**: Periodically review and update user roles
3. **Monitor Access Patterns**: Track unusual access patterns
4. **Keep System Updated**: Maintain current versions and security patches

### Performance Optimization
1. **Efficient Database Queries**: Optimize SQL queries in pages and trackers
2. **Appropriate Caching**: Use caching for frequently accessed data
3. **File Management**: Regularly clean up unused files
4. **Monitor System Resources**: Keep track of system performance metrics

---

## Support and Resources

### Getting Help
- **System Documentation**: Refer to this manual for guidance
- **Admin Support**: Contact your system administrator
- **Error Logs**: Check system logs for technical issues
- **User Community**: Connect with other portal users

### Additional Resources
- **Grails Documentation**: https://docs.grails.org/6.2.2/guide/index.html
- **Groovy Documentation**: https://groovy-lang.org/documentation.html
- **System Configuration**: Review application.yml for system settings

---

*This manual covers the essential aspects of G6Portal module management. For advanced customization and development, consult the technical documentation and system administrator.*