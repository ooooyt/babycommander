# Local Embedding Model Research & Solution

**Project:** babycommander (Quarkus 3.20, Java 21, Maven, LangChain4j 1.19.0, ObjectBox 4.3)
**Date:** 2026-08-22
**Scope:** Research document only — no code changes made. Requirement: fully local embedding model supporting **English + Chinese**, for semantic task search.

---

## 1. Current State

| Aspect | Finding |
|---|---|
| Embedding entry point | `service/EmbeddingService.java` — `embed(String) -> float[]`, best-effort, returns `null` on failure |
| Supported backends | OpenAI-compatible (`OpenAiEmbeddingModel`) and Ollama (`OllamaEmbeddingModel`) via LangChain4j |
| Activation | A provider in `agents.yaml` must declare an explicit `embeddingModel:` key (chat `modelName` is never used); currently commented out → embeddings disabled |
| Consumer | `service/ProjectTaskService` — embeds task names on create/rename; `searchProjectTasksSemantic()` embeds the query and calls `TaskRepository.findNearestNeighbors()` |
| Vector storage | `db/entity/TaskEntity.embedding` — ObjectBox **HNSW index, `dimensions = 1536`, COSINE** distance |
| Fallback | Keyword search (`findByProjectIdAndNameContaining`) when embeddings unavailable |
| Gap | No truly *local* option wired up; existing `ollama` provider points to NVIDIA cloud API |

Key constraint discovered: **vectors compared by cosine similarity must come from the same model**, and the HNSW index enforces a single fixed dimension — so exactly **one** embedding model must serve both indexing and querying, covering EN and ZH simultaneously.

---

## 2. Evaluated Options

### Option A — Ollama server + `nomic-embed-text` / `bge-m3`
- Zero code changes (`"ollama"` branch already exists); runs on `localhost:11434`.
- Cons: external process dependency; startup orchestration; not embedded in the app.
- Verdict: viable fallback, but requires users to install/run Ollama.

### Option B — In-process ONNX (LangChain4j packaged models) ✅ RECOMMENDED
- Model ships inside the application JAR; ONNX Runtime executes on CPU in-process.
- Fully offline, zero external services, zero API keys.
- Official multilingual artifact available on Maven Central (verified):
  `dev.langchain4j:langchain4j-embeddings-bge-small-zh-v15:1.19.0-beta29`
- Cons: adds ~100 MB RAM footprint; requires schema dimension change (see §4).

### Option C — LM Studio / llama.cpp server (OpenAI-compatible endpoint)
- Reuses the existing `"openai"` branch with `baseUrl: http://localhost:1234/v1`.
- Zero code changes, but heavier GUI app than Ollama; same external-process cons as A.

### Model comparison for EN + ZH

| Model | Dims | EN quality | ZH quality | Packaging |
|---|---|---|---|---|
| **bge-small-zh-v1.5** ✅ | 512 | good | excellent | official Maven artifact |
| bge-small-en-v1.5 | 384 | excellent | none | official (rejected — no ZH) |
| bge-m3 | 1024 | excellent | excellent | ⚠️ no official artifact — manual ONNX export (~600 MB int8) |

**Decision:** single-model strategy with **bge-small-zh-v1.5**. Dual-model (one per language) is rejected outright: cross-model similarity is meaningless and the HNSW index permits only one dimensionality.

---

## 3. Recommended Design (Option B)

### 3.1 Dependency (pom.xml)

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings-bge-small-zh-v15</artifactId>
    <version>1.19.0-beta29</version>
</dependency>
```

Class: `BgeSmallZhV15EmbeddingModel` (verify exact name against the jar). CPU-only,
auto-parallelized across cores by LangChain4j's default executor.

### 3.2 Schema change (critical)

`src/main/java/com/ooooyt/babycommander/db/entity/TaskEntity.java`:

```java
@HnswIndex(
    dimensions = 512,              // was 1536
    distanceType = VectorDistanceType.COSINE,
    neighborsPerNode = 30,
    indexingSearchCount = 200
)
public float[] embedding;
```

Consequences:
- ObjectBox treats this as a schema change → existing dev DBs must be wiped or migrated.
- All previously stored embeddings become invalid → one-time re-embed of existing tasks
  after upgrade (batch over tasks calling `embeddingService.embed(task.name)`).

### 3.3 EmbeddingService changes (3 small edits)

1. **Cache the model instance** — current code builds a model per `embed()` call
   (acceptable for HTTP clients, fatal for ONNX which pays heavy load cost each time):

```java
private static volatile EmbeddingModel cachedOnnxModel;
```

2. `supportsEmbeddings()`: add `|| "onnx".equals(type)`.

3. `createEmbeddingModel()` switch — add double-checked-locking case:

```java
case "onnx" -> {
    if (cachedOnnxModel == null) {
        synchronized (this) {
            if (cachedOnnxModel == null) {
                cachedOnnxModel = new BgeSmallZhV15EmbeddingModel();
            }
        }
    }
    yield cachedOnnxModel;
}
```

### 3.4 Configuration (agents.yaml)

```yaml
providers:
  local-embed:
    type: onnx
    embeddingModel: "bge-small-zh-v15"   # informational label; selects nothing else
```

No other config edits needed: `resolveEmbeddingProvider()` already scans all providers
for one declaring `embeddingModel`, so this activates automatically while cloud chat
providers remain untouched. No API key, no network access required.

---

## 4. Migration Checklist

1. Add the Maven dependency; build.
2. Change HNSW `dimensions` to `512`; delete old dev DB file (`test-app-model.dat`) or migrate.
3. Add `"onnx"` branches in `EmbeddingService` (with model caching).
4. Add the `local-embed` provider block to `agents.yaml`.
5. One-time re-embed of existing tasks.
6. Validate: searching `修复登录bug` should surface a task named `fix login bug` and vice-versa.

---

## 5. Optional Upgrade Path — bge-m3

If mixed-language retrieval quality matters more than packaging simplicity:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings</artifactId>
    <version>1.19.0-beta29</version>
</dependency>
```

```java
EmbeddingModel model = new OnnxEmbeddingModel(
        pathToModelOnnx, pathToTokenizerJson, PoolingMode.MEAN);
```

Use BAAI/bge-m3 ONNX exports placed under `src/main/resources/models/`,
set HNSW `dimensions = 1024`. Same integration points as §3 otherwise.

---

## 6. Additional Observations

- **ObjectBox OSS has no native ANN tuning beyond HNSW params** — fine at small scale;
  if task counts grow large, consider an HNSW side-index or dedicated vector store.
- **Latency:** in-process ONNX avoids network round-trips entirely; expect single-digit
  milliseconds per short text on modern CPUs vs. 50–300 ms for remote APIs.
- **Dimension mismatch guard:** `findNearestNeighbors` will throw if query vector dims
  ≠ index dims; the fallback-to-keyword path in `searchProjectTasksSemantic` already
  catches failures, but a dim check before querying would be cheap insurance.
