# Coding Conventions

## Language Standards

- **Java:** Standard Java naming conventions (PascalCase for classes, camelCase for methods/variables).
- **Hytale API:** Extensive use of Hytale registries and proxies for system/component management.

## Project Patterns

- **Logging:** Uses `Log4j2` via `LOGGER` instance defined in the main plugin class.
- **Dependency Injection:** Relies on the Hytale SDK's internal dependency management for component state and event propagation.
- **Persistence:** Direct integration with Caskara for database operations.

## Asset Naming

- **JSON Files:** PascalCase (e.g., `SimNPC_Slothian.json`) to match Hytale's expected asset format.
- **Internal IDs:** Consistent with filename but usually without the `.json` extension.
