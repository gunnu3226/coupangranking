# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot application that parses Coupang product search results and tracks product rankings over time. Users can paste HTML from Coupang search pages, extract product information, save it to a PostgreSQL database, and manage favorite products with custom display ordering.

## Build & Run Commands

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run tests
./gradlew test

# Clean build
./gradlew clean build
```

The application runs on the default Spring Boot port (8080).

## Database Configuration

- **Database**: PostgreSQL on AWS RDS
- **JPA/Hibernate**: DDL auto-update enabled
- **Connection details**: See `src/main/resources/application.yml`
- **Note**: Database credentials are currently hardcoded in application.yml

## Architecture Overview

### Core Data Flow

1. **HTML Parsing Flow**:
   - User pastes Coupang HTML → `ViewController.parseHtml()` → `HtmlParserService.extractProductList()`
   - Jsoup extracts product data (itemId, productId, name, price, ranking, ad status)
   - Products saved via `ProductDataService.saveProductData()`

2. **Data Persistence Strategy**:
   - **Product** entity: Master table storing unique products (identified by itemId + productId)
   - **ProductDailyData** entity: Time-series data linked to Product via @ManyToOne
   - **FavoriteProduct** entity: User selections with optional custom displayOrder
   - Daily data includes: date, ranking (non-ad order), display_ranking (original), currentPrice, reviewCount, productUrl, isAd

3. **Ranking Calculation Logic**:
   - `display_ranking`: Original ranking from Coupang HTML (includes ads)
   - `ranking`: Recalculated sequential order excluding ads (stored in ProductDailyData)
   - Ad products have `ranking = null`, non-ad products get sequential numbers (1, 2, 3...)

### Key Entity Relationships

```
Product (itemId, productId, productName, company, category, modelCode)
   ↓ @ManyToOne
ProductDailyData (date, ranking, display_ranking, currentPrice, reviewCount, isAd, productUrl)

Product
   ↓ @ManyToOne
FavoriteProduct (displayOrder, createdAt)
```

### Company and Category Classification

- **Company Detection**: Automatic via `ProductDataService.determineCompany()` based on product name keywords
  - Supported: IPTIME, TPLINK, NETIS, MERCUSYS, ASUS, MI
- **Category**: Currently hardcoded to `ROUTER` (CAMERA category prepared but not implemented)

### Frontend Pages & Flows

1. **index.html** (`/`): HTML input form for multi-page parsing
   - Users paste HTML from Coupang search pages
   - Supports multiple pages via dynamic textarea addition
   - Submits JSON array to `/parse` endpoint

2. **result.html** (`/parse` POST): Parsing results display
   - Shows extracted products grouped by brand and type
   - Automatically saves to database

3. **product-management.html** (`/product-management`): Master product list
   - Date-independent view of all products
   - Checkbox selection for favorites
   - "순서 편집" button → favorite-order-edit.html
   - Company-wise filtering (ipTIME, TP-Link sections)

4. **favorite-order-edit.html** (`/favorite-order-edit`): Drag-and-drop ordering
   - Reorder favorite products via drag-and-drop
   - Saves displayOrder to FavoriteProduct table via `PUT /api/favorites/order`

5. **saved-data.html** (`/saved-data`): Historical data viewer
   - Date selector for viewing past rankings
   - Tabs: 공유기 (Router) / 선택한 상품 (Favorites) / 카메라 (준비중)
   - Toggle between "순위대로 보기" (by ranking) and "내가 정한 순서로 보기" (by displayOrder)
   - Company-wise breakdown with search functionality

### REST API Endpoints

- `POST /api/favorites`: Save selected products (replaces all existing favorites)
  - Request: `{ "productIds": [1, 2, 3] }`
- `PUT /api/favorites/order`: Update display order
  - Request: `{ "orderMap": { "1": 1, "2": 2, "3": 3 } }`
- `GET /api/favorites/ordered`: Get favorites sorted by displayOrder
- `GET /api/favorites/count`: Get total favorite count

### Important Business Rules

1. **Product Uniqueness**: Products are identified by the combination of `itemId` + `productId` (both extracted from Coupang URL)
   - URL format: `/vp/products/{productId}?itemId={itemId}&vendorItemId=...`

2. **Price Format**: "원" suffix removed before storage, commas preserved

3. **Review Count Format**: Parentheses removed before storage (e.g., "(1,234)" → "1,234")

4. **Ad Detection**: Products with `data-ad-id` attribute are marked as ads

5. **Timezone**: All dates use Asia/Seoul timezone via `LocalDate.now(ZoneId.of("Asia/Seoul"))`

6. **Favorite Management**:
   - Saving favorites deletes ALL existing favorites first (replace strategy)
   - displayOrder is nullable (null values sorted last)

### HTML Parsing Implementation

`HtmlParserService.extractProductList()` uses Jsoup selectors:
- Product items: `li[id^=productItem]` or `li.search-product`
- Ad detection: presence of `[data-ad-id]` attribute
- Ranking badge: `span.badge-ranking` or `div.ranking-badge`
- Product name: `div.name`
- Current price: `strong.price-value`
- Original price: `del.base-price`
- Rating: `em.rating`
- Review count: `span.rating-total-count`

### Service Layer Responsibilities

- **HtmlParserService**: Jsoup-based HTML parsing and ProductInfo DTO creation
- **ProductDataService**: Database CRUD, company detection, date filtering, ranking calculation
- **FavoriteProductService**: Favorite management, display order updates, sorted retrieval
- **ViewController**: Thymeleaf view rendering, model preparation, date handling
- **FavoriteProductController**: REST API for favorite operations

### Frontend JavaScript Features

- **Date filtering**: `changeDate()` function reloads page with `?date=` query param
- **Table search**: Searches both itemId and productName (whitespace-normalized, case-insensitive)
- **Table copying**: `copyTableToClipboard()` uses document.execCommand('copy') for Excel export
- **Checkbox management**: `selectAllProducts()`, `deselectAllProducts()`, `updateSelectedCount()`
- **Sorting toggle**: `sortFavorites('ranking' | 'custom')` re-sorts table rows in-place via DOM manipulation
- **Drag-and-drop**: HTML5 draggable API in favorite-order-edit.html

### Common Development Patterns

1. **Adding a new Company**:
   - Add enum to `Company.java`
   - Update `ProductDataService.determineCompany()` with keyword detection
   - Add company section to HTML templates (product-management.html, saved-data.html)
   - Update `ViewController` filtering logic

2. **Adding a new Category**:
   - Add enum to `Category.java`
   - Update `ProductDataService.saveProductData()` category assignment logic
   - Create new tab in saved-data.html
   - Add filtering in ProductDataService queries

3. **Modifying HTML Parsing**:
   - Update Jsoup selectors in `HtmlParserService.extractProductList()`
   - Test with `guide.html` (sample Coupang HTML stored in resources)
   - Verify ProductInfo DTO contains all needed fields

4. **Database Schema Changes**:
   - Modify entity classes
   - DDL auto-update will apply changes (caution: may lose data)
   - Consider manual migration for production

### Testing Strategy

- Sample HTML for testing: `src/main/resources/guide.html`
- When debugging parsing issues, check Jsoup selectors against current Coupang HTML structure
- Ranking calculation can be verified by checking `ProductDailyData.ranking` vs `display_ranking` fields

### Known Limitations

- Camera category UI is present but not implemented
- No pagination for large product lists
- Database credentials are hardcoded (should use environment variables)
- No user authentication/multi-tenancy
- Favorite management replaces all selections (no incremental updates)
