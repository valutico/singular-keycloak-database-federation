# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Keycloak User Storage Provider (SPI) that enables Keycloak 17+ (Quarkus-based) to authenticate users against external relational databases. It supports PostgreSQL, MySQL, Oracle, and SQL Server, allowing existing applications to integrate with Keycloak without migrating user data.

## Essential Commands

### Build and Development
```bash
# Full build with tests
mvn clean package

# Run tests only
mvn test

# Build without tests
mvn clean package -DskipTests

# Compile only
mvn compile
```

### Deployment
```bash
# Deploy to local Keycloak installation
./deployment.sh

# Deploy to Docker container
./deployment-to-docker.sh

# Manual deployment
mvn clean package
cp ./dist/* <keycloak_root_dir>/providers
```

### Testing
```bash
# Run specific test
mvn test -Dtest=DBUserStorageProviderTest

# Run all tests
mvn surefire:test
```

## High-Level Architecture

### Core Components

**DBUserStorageProviderFactory** - Entry point using factory pattern
- Manages provider lifecycle and configuration
- Caches provider configurations per instance
- Defines all configurable properties

**DBUserStorageProvider** - Main provider implementing multiple Keycloak SPIs
- `UserLookupProvider`: User lookup by ID/username/email
- `CredentialInputValidator`: Password validation against external DB
- `ImportSynchronization`: User synchronization capabilities
- `UserQueryProvider`: User search and listing

**UserAdapter** - Bridges external DB users to Keycloak's UserModel
- Extends `AbstractUserAdapterFederatedStorage`
- Maps database columns to Keycloak attributes
- Supports attribute preservation vs. overwrite modes

**UserRepository** - Data access layer with JDBC + HikariCP
- Handles all database operations with prepared statements
- Supports multiple password hash algorithms (BCrypt, Argon2, PBKDF2-SHA256)
- Manages connection pooling and database-specific queries

### Key Design Patterns

1. **Factory Pattern with Configuration Caching** - Avoids repeated config parsing per instance
2. **Lazy Loading** - Users loaded on-demand from external database
3. **Flexible Query Configuration** - All SQL queries customizable through Keycloak admin UI
4. **Multi-Database Abstraction** - Supports 4 database types through RDBMS enum

### Data Flow
User Request → DBUserStorageProvider → UserRepository → External Database → UserAdapter → Keycloak

## Project Structure

```
src/main/java/org/opensingular/dbuserprovider/
├── model/           # UserAdapter, QueryConfigurations
├── persistence/     # DataSourceProvider, UserRepository, RDBMS
└── util/           # Password hashing, paging, SQL helpers

src/test/java/
├── mocks/          # MockDataSourceProvider
└── util/           # TestUtils
```

## Important Configuration

- **Java Version**: Java 17 (source and target)
- **Keycloak Version**: 26.2.0
- **Build Output**: `dist/` directory contains JAR and dependencies
- **Test Framework**: JUnit 4.13.2 with Mockito 5.11.0

## Password Synchronization Feature

The provider supports synchronizing password hashes from the external database to Keycloak's user store, enabling users to authenticate against Keycloak even after federation unlinking.

### Configuration Options

- **Sync Passwords**: Enable copying password hashes during user synchronization
- **Sync Query with Passwords**: Custom SQL query that includes `password_hash` column
- **Supported Hash Format**: BCrypt (Blowfish) with `$2a$`, `$2b$`, or `$2y$` prefixes

### Use Cases

1. **Federation Migration**: Gradually migrate users from external authentication to Keycloak
2. **Backup Authentication**: Provide fallback authentication when external system is unavailable
3. **User Unlinking**: Allow users to continue authenticating after removing federation link

### Security Considerations

- Password hashes are copied, not plain-text passwords
- Only BCrypt format is supported for security
- Existing Keycloak credentials are replaced during sync
- Database access controls should protect password hash columns

## Development Notes

- Uses Google AutoService for automatic SPI registration
- Connection pooling via HikariCP for database efficiency
- Supports both user sync and on-demand loading
- Read-mostly pattern - primarily sources from external DB
- All SQL queries are configurable per deployment
- Password hashing supports multiple algorithms for compatibility