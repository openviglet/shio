## 0.3.8 (Oct 10, 2021)

#### NEW FEATURES
* Blogger Importer.
* Export Folder.
* Using Github Actions.
* Hazelcache Module Parameters.

#### IMPROVEMENTS
* Nashorn improvements.
* ShCachePageLayout improvements.
* JUnit: @ParameterizedTest.
* Using zip4j.
* Code Smell and Bugs were fixed.
* Cache, Update Draft Content, Logs.
* Latest releases of libraries.
* JDK 14.
* Spring Boot 2.5.5.
* Gradle 7.0.2.

## 0.3.7 (July 28, 2020)

#### NEW FEATURES
* GraphQL
* GraphQL Documentation
* GraphiQL Console
* Background Image on Login Page
* Sidebar
* Executable Jar

#### IMPROVEMENTS
* Site Name Unique
* Code Smells
* Export Site
* Import check if package has posts.
* Using thymeleaf versioning files
* Log shows importing steps
* Using turing-sdk-java
* Spring Boot 2.3.1
* Gradle 6.5

## 0.3.6 (March 7, 2020)

#### NEW FEATURES
*  Product name was changed to Shio
* Git as Version Control
* Commit Git Commit Id on Version
* Email configuration page
* AuthProvider using /config
* OTDS as AuthProvider
* ExchangeProvider using /config
* OTCS as Exchange Provider
* OTMM as Exchange Provider
* Configuration Console
* Import Dialog to use Exchange Provider
* Page Security Configuration

#### IMPROVEMENTS
* Comment URL Formatter
* Commit Page with links
* Pre-Upload: Check if the file exists
* Void duplicated static files
* Spring Boot 2.2.5
* Gradle 6.2.2

## 0.3.5 (November 18, 2019)

#### NEW FEATURES
* Protected Pages
* Users and Groups
* Workflow and Tasks
* TinyMCE Integration
* Upload Multiple Files
* Published, Unpublished and Draft
* Checkbox Widget
* Export Folder as Spreadsheet

#### IMPROVEMENTS
* Cache Improvements
* Sample Site with new Post Type Association JSON
* History: Pagination
* Sync Nested Attributes PostType into Post
* Tomcat Special chars and removed System msgs
* Spring Boot 2.1.9 and Gradle 5.6.2

## 0.3.4 (March 04, 2019)

#### NEW FEATURES
* Multi Select Widget
* Export/Import MultiSelect (ArrayValue)
* Banner: App Version and Color
* Export Post Types from Post Type Page Layout as dependency
* Sortable for Relator
* Javascript: shObject.getRelation()
* Kubernetes: Deployments files

#### IMPROVEMENTS
* Reference JSON and it shows Javascript Code error into logs
* Delete old reference before update Post
* Turing Integration: Index using SNJob
* Reference Refactory
* Import Relator with postion
* FolderMap (JS)
* Unwrap sh-region
* Removed UI Local Files, using submodule shiohara-ui
* Removed MongoDB
* Spring Boot 2.1.3 and Gradle 5.2.1

## 0.3.3 (January 04, 2019)

#### NEW FEATURES
* Turing Semantic Navigation Settings into Post Type
* Turing Semantic Navigation Search
* Searchable Post Types
* Turing Integration: Deindexing Content
* Docker Compose

#### IMPROVEMENTS
* Sites Context: UTF-8 Encoding
* Download Site Button into Object List View
* Spring Boot 2.1.1 and Gradle 5.1

## 0.3.2 (August 27, 2018)

#### NEW FEATURES
* MongoDB
* Show content files from /sites URL: Response file with mime type
* E-Commerce: reCAPTCHA Field Type
* E-Commerce: Payment Field Type
* E-Commerce: Payment Types and Settings
* Viglet Turing Integration: Indexing Posts in Semantic Navigation
* InContext Editing using Float Menu to Regions
* Site Form: Create Forms into Site and save as Posts into Shio
* Combo Box Field Type
* Hidden Field Type
* Form Configuration Field Type

#### IMPROVEMENTS
* Spring Boot 2.0.4 and Gradle 4.9

## 0.3.1 (July 06, 2018)

#### NEW FEATURES
* Site Template: Create a new site using template
* Clone Site: Clone a site with different Ids.
* In-Context Editing: New toolbar in Preview Site, that allows edit the content directly.
* Default Folder Page Layout: Create a empty Folder using default Page Layout.

#### IMPROVEMENTS
* Spring Boot 2.0.3 and Gradle 4.8.1

## 0.3.0 (June 23, 2018)

#### NEW FEATURES
* Relator: Create fields as children of parent field.
* Sort: Drag and Drop Folders and Posts to reorder into /content, this change will reflect Navigation and Query Component order.

#### IMPROVEMENTS
* New Post Type Editor: Edit Fields of Post Types in same window without pop up. Using accordion component encapsulate entire fields details.

## 0.2.0 (May 4, 2018)

#### NEW FEATURES
* Search: You can search for objects into /content
* Content Select Widget: Create a Post Type field that allows elect some post
* File Widget: Create a Post Type field that allows select some File 

#### IMPROVEMENTS
* Unit Test
* Using H2 instead of HSQL
* Removed GlobalId because Posts, Folders and Sites Ids are unique