# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this service is

`uassist-summary` (artifact `com.uniphore.uassist:uassist-summary`) is a Spring Boot 3 / Java 17
microservice in Uniphore's U-Assist platform. It consumes conversation events from Kafka, maintains a
per-conversation `SummaryDoc` aggregate in MongoDB (fronted by Redis + local caches), calls an
external LLM inference service over gRPC to produce GenAI summaries, and publishes summary updates
back to Kafka. It also serves the REST config plane (header/intent templates, dispositions, GenAI
summary config) and handles config export/import/deployment between tenant environments.

Default HTTP port 3380. Profiles `redis,mongo,kafka` are always active.

## Build, test, run

```bash
./mvnw clean package -DskipTests=true          # build (produces target/uassist-summary.jar)
./mvnw test                                    # unit tests (*Test.java), jacoco on by default
./mvnw test -Dtest=SummaryHelperTest           # single test class
./mvnw test -Dtest=SummaryHelperTest#someCase  # single test method
./mvnw clean install test -P integration-test  # Cucumber ITs (*IT.java) — requires Docker
./docker-build.sh <tag>                        # build + push registry.uniphore.com image
garden deploy --env=dev                        # Garden deployment (see *.garden.yml)
```

- Surefire writes to `test-reports/` (not `target/surefire-reports`). Default run includes
  `*Test.java` and excludes `*IT.java`; the `integration-test` profile inverts that and points
  `testSourceDirectory` at the built jar, which is why it needs `install` first.
- Integration tests spin up Mongo/Kafka/Redis via Testcontainers; Garden passes
  `REDIS_CLUSTER`, `KAFKA_SERVERS`, `MONGO_CLUSTER` (see `uassist-summary.garden.yml`).
- CI (`.github/workflows/cicd-pipeline.yml`) delegates to the shared
  `uniphore/platform-github-actions` `ci.yml`/`cd.yml` with a SonarQube quality gate. CD runs only on
  the default branch; commits prefixed `[github-ci]` are bot chart/version bumps — don't hand-edit
  `charts/` versions.

## Dependency setup (trips people up)

- Several Uniphore libraries are **`system`-scoped jars committed under `internal-lib/`**:
  `platform-core`, `platform-cache`, `platform-telemetry`, `ri-common`, `ri-beans`,
  `common-authorization`, `feature-flag`, `encryption-utility`. Bumping e.g. `platform.version` in
  `pom.xml` also means dropping the matching jar into `internal-lib/`. The Spring Boot plugin runs
  with `includeSystemScope=true` so they land in the fat jar.
- Protobuf/gRPC stubs come from `com.uniphore:uniphore-protos-java`, pinned to a git sha
  (currently `7be0778`) and hosted on GitHub Packages (`maven.pkg.github.com/uniphore/protos-java`) —
  needs GitHub credentials in `~/.m2/settings.xml`.
- `encryption-windows.so` at the repo root is the native lib for `encryption-utility` (loaded via JNA).

## Architecture

### Ingress: Kafka functions + REST

Messaging uses Spring Cloud Stream functional bindings. Every consumer/producer is declared in
`src/main/resources/application-kafka-binder.yml`:
`spring.cloud.function.definition: conversationEvents;deploymentRequest;agentUpdate;tenantOperation`.
Adding a consumer means: `@Bean Consumer<...>` + append to that definition list + add a
`<name>-in-0` binding. Outbound binding names are constants in `conf/DataStreamConfig` and everything
is published through `kafka/MessagePublisher` (StreamBridge), never `KafkaTemplate` directly.

`kafka/MessageConsumer.conversationEvents` is the hot path. It sets tenant/span context, checks
LaunchDarkly, filters by `ProductType`, then either calls
`SummaryRuntimeService.handleConversationEvent` inline or (when
`LaunchDarklyConstants.SUMMARY_PARALLEL_PROCESS_ENABLED` is on) hands the event to the in-process
queue. Consumers deliberately catch and log exceptions so Kafka offsets keep advancing.

### Event fan-out and the per-pod queue

- `event/service/ConversationEventQueueManager` decides whether an event matters
  (`checkSummaryEffectedEvent`) and whether the conversation is known to this pod. It keeps a
  `conversationIdLastUpdatedTimeStamp` map as a fast path so it doesn't hit Mongo for every event;
  on a miss (e.g. after a consumer rebalance) it looks up the `SummaryDoc` and drops the event if
  none exists.
- `event/processing/ConversationEventQueue` is a single `Thread` + `LinkedBlockingQueue` per pod
  draining into `SummaryRuntimeService`. Ordering is process-wide FIFO, not per-conversation.
- `event/scheduler/ConversationProcessorMapCleaner` evicts conversations idle longer than
  `conversations.processor.scheduler.processor-idle-duration`.

### Event handling core

`service/SummaryRuntimeServiceImpl` (~2.3k lines) is the heart of the service.
`handleConversationEvent` is a long if/else chain over the `ConversationEventEnvelope` proto's
event fields (`entityAdded`, `entityUpdated`, `variablesAdded/Updated`, `intentsAdded/Deleted`,
`conversationStarted`, `flowEnded`, `stateUpdated`/`stateV2Updated`, `policiesAdded`,
`transcriptChunkAdded`), testing presence with `StringUtils.isNotEmpty(env.getX().toString())`.
**A new event type needs a branch in both `handleConversationEvent` and
`ConversationEventQueueManager.checkSummaryEffectedEvent`** — miss the second and the event never
reaches the queue.

### Persistence and the three-tier cache

- `dao/documents/SummaryDoc` (`@Document("Summary")`, `_id` = conversationId) is the aggregate:
  header summary, intent summaries, entity/variable slots, agent notes, `genAiSummary` +
  `genAiGeneratedSummary`, `templateId`/`templateVersion`, and a loose `metadata` map (turn counts,
  `EventEndTime`).
- DAO convention: `*Repo`/`*Repository` are Spring Data `MongoRepository` interfaces; `*Dao` are
  `@Component` wrappers that add `MongoTemplate` queries, tenant/environment filtering, and `@Async`
  writes (`threadPoolMongoExecutor`). Service code goes through the Dao or a `*Helper`, not the
  repository.
- Reads walk **Caffeine local cache → Redis → Mongo**. Local caches are the `HashLocalCache`/
  `ListLocalCache` beans in `cache/LocalCacheBeans` (TTLs `local.cache.ttl.runtime|config`); Redis
  access is `CacheOperations<T>` from the platform-cache jar. `GenAISummaryConfigCacheHelper.find` is
  the canonical example.
- **Every cache key is tenant+environment scoped** through `cache/CommonCacheHelper`
  (`uniphore.summary.config.<tenantId>.<env>.<type>`), derived from `TenantContext`. Any code path
  touching cache or Mongo must run with tenant context established — consumers and stream listeners
  set it explicitly at entry via `ContextSetter`/`TenantContext`, and clear it in `finally`.
- Cross-pod local-cache invalidation rides Redis Streams: `redisstream/ConfigChangeEventPublisher` →
  `ConfigChangeEventListener` deletes the corresponding local entries. **A config write that doesn't
  publish a change event leaves other pods serving stale config** for up to
  `local.cache.ttl.config`. A second stream (`uassist-summary-agent-update`) fans out agent edits.

### GenAI summary generation

- `service/GenAISummaryService.createGenAiSummary` orchestrates; `util/GrpcClientHelper` holds the
  blocking `LLMInferenceServiceGrpc` stub (`gen-ai.interface.*` properties).
- Duplicate-generation guard: `SummaryCacheHelper.acquireGenAiSummaryGenerationLock` has an
  **inverted return** — `false` means this caller took the lock, `true` means someone else holds it.
  The loser then polls Mongo up to 20×1s in `checkSummaryInDbWithRetries`.
- Request shape is flag-dependent: NG template mode (`fs-builder-mode`) sends `templateId` +
  `version`; otherwise a section-based request (call reason / agent actions / call outcome /
  customer sentiment) modulated by opt-in-out, entity-enhancement, and target-language flags.
  "Short call" config can skip the LLM entirely and store a canned message.
- LaunchDarkly gating is pervasive (`LaunchDarklyHelper.isFFEnabled`, flag keys in
  `common/Constants` and `LaunchDarklyConstants` from the feature-flag jar). New behaviour normally
  goes behind a flag; `controller/FeatureFlagController` exposes flag state to the UI.

### Config plane, deployment, tenancy

- Controllers: `SummaryRuntimeController` (runtime `/v1/...` agent notes/edits/intents/review),
  `GenAISummaryConfigController`, `HeaderTemplateController`, `IntentTemplateController`,
  `DispositionController`, `ExportImportController`, `DeploymentController`, `MetricController`
  (`/v2/report-by-convid`), `HealthController` (`/health/startup|liveness|readiness`).
- Environment promotion: Kafka `environments.deployment-events` → `deployment/stream/
  DeploymentTaskListener` → `DeploymentService`; export/import/rollback run on their own executors
  and reply on `environments.export-response` / `import-response` / `rollback-response`.
- `kafka/TenantOperationEventConsumer` handles tenant-level operations (e.g. deletion) by sweeping
  every repository.

### Observability

Datadog `dd-java-agent` + statsd, Micrometer with Prometheus at `/actuator/prometheus`,
OpenTelemetry OTLP metrics, plus `PlatformTelemetry`/`ContextSetter` from platform-telemetry for
tenant-tagged spans. `common/CustomMetricsPublisher` for internal counters;
`helper/LookerGenAISummaryMetricHelper` emits product metrics as `ConversationMetric` protos to the
`metrics-reporter` topic.

## Conventions

- 2-space indent, Lombok (`@Data`/`@Builder`/`@Slf4j`), field injection via `@Autowired`.
- All configuration is `${ENV_VAR:default}` in `application*.yml`; put new settings in the matching
  profile file (`application-kafka|kafka-binder|mongo|redis.yml`) and mirror them into
  `src/test/resources/` so the Cucumber context still starts.
- Errors: `execption/CustomException` + `ErrorCodes` + `ExceptionAdviceHandler` (note the misspelled
  package name `execption`).
- Controllers take the caller identity from the `X-Username` request attribute
  (`Constants.X_USERNAME`, populated by the common-authorization filter) and overwrite any `agentId`
  in the payload — keep it that way.

## Tests

- Unit tests mirror the main package tree under `src/test/java/...`, named `*Test.java`.
- Integration tests are Cucumber features in `src/test/resources/features/` run by
  `integrationtests/CucumberIT`, with glue in `integrationtests/cucumberglue/`.
  `cucumberglue/ApplicationContextTest` is the shared Spring context: Testcontainers initializers
  (`RedisConfigTest`, `KafkaConfigTest`, `MongoConfigInitializer`) plus `@MockBean` for
  `LaunchDarklyHelper`, `GrpcClientHelper`, and `CustomMetricsPublisher` — so ITs never call the real
  LLM or LaunchDarkly, and any new feature flag must be stubbed on the mock in the glue code.
- `integrationtests/IntegrationTestHelper.publishKafka` is how features inject
  `ConversationEventEnvelope` messages onto the `conversation-events` topic.
