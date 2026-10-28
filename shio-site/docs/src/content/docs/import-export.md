---
title: Import & Export
description: Exchange providers, content migration, and site backup/restore in Viglet Shio.
sidebar:
  order: 2
---

Viglet Shio provides built-in import/export functionality for content migration, backup, and integration with external content management systems.

---

## Site Export

Export a complete site as a downloadable package:

1. Navigate to the site in the admin console
2. Click **Download Site**
3. The package includes:
   - All folders and posts
   - Post Types and their attributes
   - Page Layouts and Regions
   - Themes and static files
   - Site configuration

The exported package can be used for backup, migration, or duplication.

---

## Site Import

Import a previously exported site package:

1. Navigate to **Sites** in the admin console
2. Click **Import Site**
3. Upload the site package file

The import process creates all folders, posts, Post Types, and configuration from the package.

### API Import

You can also import programmatically via the REST API:

```
POST /api/v2/import
Content-Type: multipart/form-data
```

---

## Exchange Providers

Exchange providers enable content import from external content management systems. Providers are configured in **Configuration Console > Exchange Providers**.

### OTCS: OpenText Content Services

Import documents from **OpenText Content Services**:

| Feature | Details |
|---|---|
| **Source** | OpenText Content Services (OTCS) |
| **Content** | Documents, folders, and metadata |
| **Authentication** | OTCS credentials |

### OTMM: OpenText Media Management

Import media files from **OpenText Media Management**:

| Feature | Details |
|---|---|
| **Source** | OpenText Media Management (OTMM) |
| **Content** | Images, videos, and media assets |
| **Authentication** | OTMM credentials |

### Blogger

Import blog posts from **Google Blogger**:

| Feature | Details |
|---|---|
| **Source** | Blogger blog export |
| **Content** | Blog posts, titles, dates, and content |
| **Plugin** | `com.viglet.shio.plugin.ShImporterBloggerPlugin` |

---

## Content Operations

### Folder Export

Export individual folders and their contents as spreadsheets. Each Post Type in the folder becomes a separate sheet with columns for each field.

### Bulk Upload

Upload multiple files to a folder at once through the admin console. Files are automatically stored and accessible via the Static File API.

---

## Related Pages

| Page | Description |
|---|---|
| [Administration Guide](/docs/administration-guide/) | Site management and configuration |
| [REST API Reference](/docs/rest-api/) | Import API endpoint |
| [Content Modeling](/docs/content-modeling/) | Post Types and fields |

---
