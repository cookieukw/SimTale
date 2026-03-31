# Technical Concerns

## Identified Issues

- **Test Coverage:** Zero automated test coverage complicates refactoring and long-term maintenance.
- **Dependencies:** Hard dependency on `caskara` and local Hytale SDK paths makes the project less portable without specific setup.
- **Documentation:** Limited internal documentation for complex NPC behavior logic and social stat calculations.

## Technical Debt

- **SDK Pathing:** reliance on `local.properties` for absolute SDK paths (`hytale.dir`) can cause build failures in new environments if not configured correctly.
- **NPC Factory Logic:** Spawning logic is somewhat coupled with the main plugin setup, which might benefit from further modularization.
