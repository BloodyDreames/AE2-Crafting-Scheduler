# Local dependency overrides

The project normally resolves Applied Energistics 2 and GuideME from Maven Central. No third-party
JAR files are stored in this repository.

The `libs/` directory is reserved for temporary local experiments. Files matching `libs/*.jar` are
ignored by Git and are not used by the default build.

Dependency versions are pinned in [`gradle.properties`](../gradle.properties).
