# Blog Site Example

A modern blog example for Viglet Shio demonstrating the Universal Editor capabilities.

## Structure

- `export.json` - Shio Exchange format file for importing into Viglet Shio
- `index.html` - Blog listing page with `data-sh-*` instrumentation
- `post.html` - Single post page with editable content

## How to Import

1. Create a ZIP file containing `export.json`
2. In Viglet Shio, go to **Content > Import Site**
3. Upload the ZIP file

## How to Use with Universal Editor

1. Import the site into Viglet Shio
2. Navigate to **Universal Editor** in the sidebar
3. Select the "Tech Blog" site
4. Switch to **Edit Mode** and click on any highlighted element

## Post Types

### Article
- **TITLE** (Text) - Article title
- **EXCERPT** (Text Area) - Short excerpt shown in listings
- **COVER_IMAGE** (Text) - Cover image URL
- **CONTENT** (HTML Editor) - Full article body
- **AUTHOR** (Text) - Author name
- **CATEGORY** (Text) - Article category
- **PUBLISHED_DATE** (Date) - Publication date
