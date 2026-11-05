---
title: GraphQL
description: Query content in Viglet Shio using GraphQL with the built-in GraphiQL console.
sidebar:
  order: 7
---

Viglet Shio provides a **GraphQL** API for querying content. GraphQL is ideal for decoupled frontends, mobile applications, and any scenario where you need to request exactly the data you need in a single request.

---

## GraphiQL Console

Shio includes a built-in **GraphiQL** interactive console for exploring and testing GraphQL queries:

```
http://localhost:2710/graphiql
```

The console provides:
- **Schema explorer**: browse available types and fields
- **Auto-complete**: type-ahead suggestions for queries
- **Query history**: review and re-run previous queries
- **Documentation**: inline documentation for all types

---

## Querying Content

### Query Posts

Retrieve posts from a specific folder:

```graphql
{
  posts(folderId: "folder-uuid") {
    id
    title
    summary
    postType
    publishStatus
    date
    attributes {
      name
      value
    }
  }
}
```

### Query a Single Post

```graphql
{
  post(id: "post-uuid") {
    id
    title
    summary
    postType
    publishStatus
    attributes {
      name
      value
    }
  }
}
```

### Query Folders

```graphql
{
  folders(siteId: "site-uuid") {
    id
    name
    parentFolder
    children {
      id
      name
    }
  }
}
```

### Query Sites

```graphql
{
  sites {
    id
    name
    description
    url
  }
}
```

---

## Use Cases

| Use Case | Why GraphQL |
|---|---|
| **Single-Page Applications** | Fetch exactly the fields you need in one request |
| **Mobile Apps** | Minimize payload size by selecting specific fields |
| **Content Aggregation** | Combine posts, folders, and site data in a single query |
| **Prototyping** | Use GraphiQL to explore the content model interactively |

---

## Authentication

GraphQL queries require authentication. Use session-based authentication (same as the REST API) or configure API access as needed.

---

## Related Pages

| Page | Description |
|---|---|
| [REST API Reference](/docs/rest-api/) | Full REST API endpoint documentation |
| [Content Modeling](/docs/content-modeling/) | Post Types and fields |
| [Developer Guide](/docs/developer-guide/) | Dev environment and project structure |

---
