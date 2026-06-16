# IntelliDesk Backend

AI-powered IT Support Knowledge Assistant - Spring Boot backend with RAG pipeline, LangGraph-style workflow orchestration, and multi-turn conversation support.

## Prerequisites

- Java 17+ (JDK)
- Gradle 8.x (or use the included Gradle Wrapper)

## Setup

1. **Generate Gradle Wrapper** (if `gradle-wrapper.jar` is missing):
   ```bash
   gradle wrapper --gradle-version=8.10.2
   ```

2. **Configure environment**:
   ```bash
   cp .env.example .env
   # Edit .env with your API keys and configuration
   ```

3. **Build the project**:
   ```bash
   ./gradlew build
   ```

4. **Run tests**:
   ```bash
   ./gradlew test
   ```

5. **Run the application**:
   ```bash
   ./gradlew bootRun
   ```

## Project Structure

```
src/main/java/com/intellidesk/
├── api/            # REST API layer - controllers, DTOs, exception handlers
├── workflow/       # LangGraph-style directed state graph orchestration
├── session/        # Multi-turn conversation context management
├── resilience/     # Circuit breakers, retry logic, graceful degradation
├── rag/            # RAG pipeline - ingestion, chunking, retrieval
├── security/       # Rate limiting, input sanitization, CORS, file validation
├── evaluation/     # Offline evaluation runner with metrics computation
├── config/         # Application configuration, profiles, env loading
└── IntellideskApplication.java
```

## Configuration

Configuration is loaded from environment variables and `.env` files. Environment variables take precedence.

### Profiles

- **local** (default): Development with local services (ChromaDB, mock STT)
- **dev**: Shared development environment
- **prod**: Production with managed services (Pinecone, Deepgram)

Set the profile via: `APP_PROFILE=dev`

See `.env.example` for all configurable properties and their defaults.

## Testing

- **Unit tests**: JUnit 5 + Mockito
- **Property-based tests**: jqwik
- **Coverage**: JaCoCo (≥80% service layer coverage required)

```bash
# Run all tests
./gradlew test

# Generate coverage report
./gradlew jacocoTestReport
# Report available at: build/reports/jacoco/test/html/index.html
```
