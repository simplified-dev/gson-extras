# Gson Extras

Custom Gson `TypeAdapter` implementations, annotation-driven `TypeAdapterFactory` extensions, and a centralized `GsonSettings` builder for configuring `Gson` instances. Provides adapters for `Color`, `Instant`, `OffsetDateTime`, `String` (with null-safety), and `UUID`, plus factories for `Optional` unwrapping, case-insensitive enums, nested JSON path mapping, dynamic key capture, lenient type filtering, string-delimited pair splitting, and post-deserialization callbacks.

> [!IMPORTANT]
> This library is under active development. APIs may change between releases
> until a stable `1.0.0` release is published.

## Table of Contents

- [Features](#features)
  - [Adapters](#adapters)
  - [Factories](#factories)
  - [Annotations](#annotations)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
- [Usage](#usage)
  - [Quick Start](#quick-start)
  - [Custom Configuration](#custom-configuration)
  - [Individual Adapters](#individual-adapters)
- [Architecture](#architecture)
  - [Package Overview](#package-overview)
  - [Project Structure](#project-structure)
- [Dependencies](#dependencies)
- [Contributing](#contributing)
- [License](#license)

## Features

`GsonSettings` provides immutable, builder-based configuration for `Gson` instances. Call `GsonSettings.defaults()` to get a pre-configured instance with all adapters and factories registered, or use `GsonSettings.builder()` to assemble a custom configuration.

### Adapters

| Adapter | Java Type | Description |
|---------|-----------|-------------|
| `ColorTypeAdapter` | `java.awt.Color` | Serializes as a single RGBA-packed integer; deserializes from integer or comma-separated `r,g,b,a` string |
| `InstantTypeAdapter` | `java.time.Instant` | Serializes as epoch milliseconds; deserializes from epoch millisecond values |
| `OffsetDateTimeTypeAdapter` | `java.time.OffsetDateTime` | Serializes and deserializes using ISO-8601 format with timezone offset via `DateTimeFormatter.ISO_OFFSET_DATE_TIME` |
| `StringTypeAdapter` | `java.lang.String` | Configurable null/empty handling via `StringType` - `DEFAULT` passes through, `EMPTY` converts null to empty, `NULL` converts empty to null |
| `UUIDTypeAdapter` | `java.util.UUID` | Serializes via `toString()`; deserializes from standard UUID string representation |

### Factories

| Factory | Trigger | Description |
|---------|---------|-------------|
| `PostInitTypeAdapterFactory` | `PostInit` interface | Wraps deserialization to call `postInit()` after all field population is complete, allowing computed or transient fields to be initialized from deserialized state |
| `CaptureTypeAdapterFactory` | `@Capture` | Collects unmatched JSON keys into typed `Map` fields; supports regex filtering to route keys by prefix, enum keys via `@SerializedName`, and class-value grouping where flat keys like `song_hymn_joy_best_completion` are auto-grouped into nested objects |
| `LenientTypeAdapterFactory` | `@Lenient`, `@Extract` | Silently filters type-incompatible entries from `Map` and `Collection` fields during deserialization, storing filtered entries as overflow for round-trip fidelity; `@Extract` pulls specific overflow entries into typed companion fields via dot-path syntax |
| `SerializedPathTypeAdaptorFactory` | `@SerializedPath` | Maps flat Java fields to nested JSON paths using dot-separated expressions (e.g. `stats.combat.strength`), restructuring the JSON tree on both read and write to maintain nested structure |
| `SplitTypeAdapterFactory` | `@Split` | Splits a single JSON string value by a literal delimiter into the two generic type components of a `Pair` or `PairOptional` field, rejoining on serialization |
| `OptionalTypeAdapterFactory` | `Optional<T>` | Unwraps present values to their inner type during serialization; deserializes null or missing values as `Optional.empty()` |
| `CaseInsensitiveEnumTypeAdapterFactory` | Any enum | Matches enum constants by name and `@SerializedName` values (including alternates) without regard to case during deserialization; serializes using `@SerializedName` value when present |

### Annotations

| Annotation | Target | Description |
|------------|--------|-------------|
| `@Capture` | `Map` field | Marks a field to receive unmatched JSON entries; optional `filter` regex selects keys by pattern and strips the matched prefix from captured key names |
| `@Extract` | Field | Pulls a specific entry from a `@Lenient` field's overflow into a typed companion field using `"sourceField.jsonKey"` path syntax |
| `@Lenient` | `Map`/`Collection` field | Enables lenient deserialization that silently discards type-incompatible entries rather than failing, preserving them as overflow for serialization round-trips |
| `@SerializedPath` | Field | Specifies a dot-separated path (e.g. `"stats.combat.strength"`) mapping a flat Java field to a nested JSON location |
| `@Split` | `Pair`/`PairOptional` field | Specifies a literal delimiter string used to split a single JSON string into left and right components for `Pair`/`PairOptional` deserialization |

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [Java](https://adoptium.net/) | **21+** | Required (LTS recommended) |
| [Gradle](https://gradle.org/) | **9.4+** | Or use the included `./gradlew` wrapper |
| [Git](https://git-scm.com/) | **2.x+** | For cloning the repository |

### Installation

Add the JitPack repository and dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.simplified-dev:gson-extras:master-SNAPSHOT")
}
```

<details>
<summary>Gradle (Groovy)</summary>

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.simplified-dev:gson-extras:master-SNAPSHOT'
}
```

</details>

<details>
<summary>Maven</summary>

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.simplified-dev</groupId>
    <artifactId>gson-extras</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
```

</details>

> [!NOTE]
> This library depends on other Simplified-Dev modules (`collections`, `utils`,
> `reflection`) and on Google Gson, which are resolved from JitPack and Maven
> Central automatically.

## Usage

### Quick Start

`GsonSettings.defaults()` returns a fully configured settings instance with all
built-in adapters and factories registered:

```java
import com.google.gson.Gson;
import dev.simplified.gson.GsonSettings;

Gson gson = GsonSettings.defaults().create();

// Serialize
String json = gson.toJson(myObject);

// Deserialize
MyObject obj = gson.fromJson(json, MyObject.class);
```

### Custom Configuration

Use `defaults()` as a starting point and `mutate()` to customize:

```java
Gson gson = GsonSettings.defaults()
    .mutate()
    .isPrettyPrint()
    .isSerializingNulls()
    .withStringType(GsonSettings.StringType.NULL)
    .build()
    .create();
```

Or build from scratch with `GsonSettings.builder()` for full control:

```java
Gson gson = GsonSettings.builder()
    .withTypeAdapter(UUID.class, new UUIDTypeAdapter())
    .withFactories(new OptionalTypeAdapterFactory())
    .isPrettyPrint()
    .build()
    .create();
```

### Individual Adapters

You can also register adapters directly on a `GsonBuilder`:

```java
import com.google.gson.GsonBuilder;
import dev.simplified.gson.adapter.InstantTypeAdapter;
import dev.simplified.gson.adapter.UUIDTypeAdapter;

Gson gson = new GsonBuilder()
    .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
    .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
    .create();
```

## Architecture

### Package Overview

| Package | Description |
|---------|-------------|
| `dev.simplified.gson` | `GsonSettings` builder, `StringType` enum, annotations, and `PostInit` interface |
| `dev.simplified.gson.adapter` | `TypeAdapter` implementations for individual Java types |
| `dev.simplified.gson.factory` | `TypeAdapterFactory` implementations for annotation-driven behaviors |

### Project Structure

```
src/main/java/dev/simplified/gson/
├── GsonSettings.java
├── Capture.java
├── Extract.java
├── Lenient.java
├── PostInit.java
├── SerializedPath.java
├── Split.java
├── adapter/
│   ├── ColorTypeAdapter.java
│   ├── InstantTypeAdapter.java
│   ├── OffsetDateTimeTypeAdapter.java
│   ├── StringTypeAdapter.java
│   └── UUIDTypeAdapter.java
└── factory/
    ├── CaptureTypeAdapterFactory.java
    ├── CaseInsensitiveEnumTypeAdapterFactory.java
    ├── LenientTypeAdapterFactory.java
    ├── OptionalTypeAdapterFactory.java
    ├── PostInitTypeAdapterFactory.java
    ├── SerializedPathTypeAdaptorFactory.java
    └── SplitTypeAdapterFactory.java
```

## Dependencies

| Dependency | Version | Scope |
|------------|---------|-------|
| [Gson](https://github.com/google/gson) | 2.11.0 | API |
| [JetBrains Annotations](https://github.com/JetBrains/java-annotations) | 26.0.2 | API |
| [Lombok](https://projectlombok.org/) | 1.18.36 | Compile-only |
| [collections](https://github.com/Simplified-Dev/collections) | master-SNAPSHOT | API (Simplified-Dev) |
| [utils](https://github.com/Simplified-Dev/utils) | master-SNAPSHOT | API (Simplified-Dev) |
| [reflection](https://github.com/Simplified-Dev/reflection) | master-SNAPSHOT | API (Simplified-Dev) |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style
guidelines, and how to submit a pull request.

## License

This project is licensed under the **Apache License 2.0** - see
[LICENSE.md](LICENSE.md) for the full text.
