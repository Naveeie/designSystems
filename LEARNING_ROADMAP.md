# 🚀 U-Assist Summary Service - Production Learning Roadmap (Revised)

> **Goal**: Learn to read, debug, and reason about production code by tracing real scenarios through actual code.
> 
> **Method**: Scenario-based learning + just-in-time code knowledge + production thinking.
> 
> **Not**: Definition memorization. **Yes**: "What breaks? Why this design? How would you fix it?"

---

## 🎯 Learning Philosophy

You already know Java and Spring Boot **in theory**. Your gap is:
- **Not** understanding `@Autowired` (you know it)
- **Yes** understanding why this codebase chose field injection over constructor injection
- **Not** what Kafka is (you know it)
- **Yes** what happens when this codebase processes the same message twice

**New approach**:
1. Start with real scenario
2. Read the actual code that handles it
3. Trace execution line by line
4. Predict what breaks
5. Verify against code
6. Understand the design decision

---

## 📚 Code Knowledge Sections (Just-In-Time)

Whenever we encounter unfamiliar Java/Spring:

### **Quick Refresher Format**:
```
🔧 CODE KNOWLEDGE CHECKPOINT: CompletableFuture

What it is: Represents a future result (like a Promise in JavaScript)

You'll see:
  CompletableFuture<SummaryDoc> future = dao.save(doc);
  // Returns immediately, save happens in background
  
Common operations:
  future.get()           // Wait for result (blocking)
  future.thenApply(x)    // Chain operation
  future.join()          // Same as get() but unchecked exception

Why this repo uses it: Database writes are async (@Async), don't block HTTP response

Production impact: If you forget to wait for it, write might not complete before pod restarts
```

We'll do these inline as we encounter them.

---

# 📋 REVISED CURRICULUM: Scenario-Based Learning

## PHASE 1: Understand ONE Complete Flow (Days 1-3)

### **Scenario 1: Agent Adds a Note to Summary**

A support agent finishes a call and adds a note: "Customer was frustrated about billing."

This note must:
- Arrive at the service
- Update the summary in MongoDB
- Be visible in cache immediately
- Trigger a Kafka event
- Work correctly even if multiple agents add notes simultaneously

**Your job**: Trace this from HTTP request → database → cache → Kafka publish.

---

#### **Step 1: What Request Arrives?**

```http
POST /v1/summary/conversation-abc-123/notes
Content-Type: application/json
X-Username: agent-john-doe

{
  "text": "Customer frustrated about billing",
  "agentId": "agent-123"
}
```

**Questions**:
- Where does this request land in the code?
- What's the `@RequestMapping`?
- Who extracts `X-Username`?
- Why does the controller overwrite `agentId` in the payload?

---

#### **Step 2: Find the Controller**

**Your task**: 
1. Find `SummaryRuntimeController.java`
2. Find the method that handles POST to `/notes`
3. Read the method signature
4. Write down what you see

**Code knowledge checkpoint** (if needed):
```java
@RestController          // Marks this as HTTP handler
@RequestMapping("/v1")  // All methods start with /v1
public class SummaryRuntimeController {
  
  @PostMapping("/summary/{id}/notes")  // POST /v1/summary/{id}/notes
  public ResponseEntity<?> addNote(
    @PathVariable String id,           // Extract from URL: {id}
    @RequestBody AgentNote note        // Parse JSON body to object
  ) {
    // ...
  }
}
```

---

#### **Step 3: Controller Calls Service**

**Your task**:
1. In that controller method, find what it calls
2. What service method? What parameters?
3. What does it return?

**Code knowledge checkpoint** (if needed):
```java
@Autowired                          // Spring injects this
private SummaryRuntimeService summaryRuntimeService;

public ResponseEntity<?> addNote(...) {
  summaryRuntimeService.handleAgentNote(id, note);  // Calls service
  return ResponseEntity.ok("Note added");
}
```

---

#### **Step 4: Service Updates Summary**

**Your task**:
1. Find `SummaryRuntimeService.java`
2. Find the `handleAgentNote()` method
3. Trace what it does:
   - Does it read from database first?
   - Does it update the object?
   - Does it write to database?
   - Does it publish an event?
4. Write down the execution order

**Code knowledge checkpoint** (if needed):
```java
@Service  // Spring manages this bean
public class SummaryRuntimeService {
  
  @Autowired
  private SummaryRuntimeDao dao;  // Handles database
  
  public void handleAgentNote(String id, AgentNote note) {
    SummaryDoc doc = dao.findById(id);  // Read from DB/cache
    doc.getAgentNotes().add(note);      // Modify in memory
    dao.save(doc);                       // Write async
    publishEvent(doc);                   // Publish to Kafka
  }
}
```

---

#### **Step 5: DAO Layer - Tenant Context**

**Critical checkpoint**: Before reading/writing, where does tenant info come from?

**Your task**:
1. In `SummaryRuntimeDao.findById()`, trace how it finds the document
2. Does it filter by tenant?
3. Where does tenant ID come from?
4. What's ThreadLocal?

**Code knowledge checkpoint** (if needed):
```java
🔧 JAVA REFRESHER: ThreadLocal

What it is: Thread-specific storage. Each thread has its own copy.

Example:
  ThreadLocal<String> tenantId = new ThreadLocal<>();
  
  Thread A: tenantId.set("tenant-a")
  Thread B: tenantId.set("tenant-b")
  
  Thread A: tenantId.get() → "tenant-a" (not "tenant-b"!)
  Thread B: tenantId.get() → "tenant-b"
  
  // Each thread has separate storage

Why in this repo: Each HTTP request on different thread. 
                  Tenant info per-request, no mixing.

Gotcha: If thread is reused (thread pool), must clear()!
```

**Now find**:
1. Where is `TenantContext` set? (trace to Kafka consumer or HTTP filter)
2. How does DAO use it?
3. What's in the cache key?

---

#### **Step 6: MongoDB Write (Async)**

**Your task**:
1. Find `@Async` annotation on `dao.save()`
2. What does `@Async` mean?
3. When does the method return vs. when does write complete?
4. Why async instead of sync?

**Code knowledge checkpoint** (if needed):
```java
🔧 SPRING REFRESHER: @Async

Without @Async (sync):
  public void save(SummaryDoc doc) {
    db.write(doc);  // WAIT HERE until write finishes (10ms-100ms)
    return;         // Then return
  }
  // HTTP response delayed by database latency

With @Async:
  @Async("threadPoolMongoExecutor")  // Specify thread pool
  public CompletableFuture<SummaryDoc> save(SummaryDoc doc) {
    db.write(doc);  // Happens in DIFFERENT thread
    return CompletableFuture.completedFuture(result);
    // Return immediately, write happens in background
  }
  // HTTP response fast, write continues in background

Gotcha: If pod restarts, background write might not complete!
        If you need to wait: future.get() blocks until done
```

**Production question**:
- "If a pod dies 5 seconds after save() returns, what happens to the write?"

---

#### **Step 7: Cache Update**

**Your task**:
1. When `dao.save()` completes, is cache updated?
2. Find where cache is updated
3. What's the cache key?
4. Does tenant ID appear in the key?

**Code knowledge checkpoint** (if needed):
```java
🔧 SPRING REFRESHER: Redis Keys with Tenant

Wrong (no tenant):
  cache.set("summary:conv-123", doc);
  // Tenant A and Tenant B both use same key!
  // Tenant B overwrites Tenant A's cache!

Right (with tenant):
  String key = "uniphore.summary.tenant-a.prod.conv-123";
  cache.set(key, doc);
  // Different cache entry per tenant
  // No collision
```

**Production question**:
- "What if cache key doesn't include tenant ID? What breaks?"

---

#### **Step 8: Kafka Event Publishing**

**Your task**:
1. Find where Kafka event is published
2. What event contains?
3. What topic?
4. At what point in the flow (before or after database write?)?

**Code knowledge checkpoint** (if needed):
```java
🔧 SPRING REFRESHER: Spring Cloud Stream

What it is: Abstraction for publishing messages to Kafka (or RabbitMQ)

You'll see:
  @Bean
  public Supplier<Message<SummaryUpdateEvent>> summaryUpdate() {
    return () -> MessageBuilder.withPayload(event).build();
  }
  
  Or: Use StreamBridge directly:
  streamBridge.send("summaryUpdate-out-0", event);

Why: Decouples from Kafka. Could swap to RabbitMQ without code change.
```

**Production question**:
- "Should the Kafka publish happen before or after the database write succeeds?"
- "What if Kafka is down? Does the write still happen?"

---

#### **Step 9: Full Trace Summary**

**Your task**: Write down the complete flow:

```
HTTP POST /v1/summary/conv-123/notes
  ↓ (what class?)
  ↓ (what method?)
  ↓ (what parameters extracted?)
SummaryRuntimeService.handleAgentNote()
  ↓
TenantContext.set()  (where? by whom?)
  ↓
SummaryRuntimeDao.findById()
  ↓ (check tenant context? check cache?)
MongoDB read (or Redis hit)
  ↓
Service modifies in memory
  ↓
SummaryRuntimeDao.save() [@Async]
  ↓ (returns immediately or waits?)
MongoDB write (in background thread)
  ↓
Cache updated (with tenant-scoped key)
  ↓
Kafka event published
  ↓
HTTP 200 response
```

---

### **Production Questions on Scenario 1**

Now that you've traced the code, answer these (from code understanding, not theory):

1. **Concurrency**: 
   - "Two agents call this endpoint simultaneously for same conversation. What happens?"
   - Trace the code: Is there a lock? What happens if both modify in-memory object?
   - What would you add to prevent data loss?

2. **Tenant Isolation**:
   - "What happens if agent from Tenant B tries to add note to Tenant A's conversation?"
   - Is there a check in the code? Where?
   - What if that check is removed?

3. **Async Failure**:
   - "Service calls `dao.save()` and returns HTTP 200. Then pod crashes 1 second later."
   - Did the note get saved to MongoDB?
   - What should the client do?

4. **Cache Staleness**:
   - "HTTP response sent, but cache update hasn't happened yet (still in thread pool queue)."
   - Next request reads from cache. Does it get old data or new data?
   - When would this cause problems?

5. **Multi-Pod**:
   - "Pod A processes the note and updates Redis cache."
   - "Meanwhile, Pod B is reading the same conversation from cache."
   - Does Pod B see the update immediately? Or does it have stale data?
   - Trace the code: How long is local cache TTL?

---

### **Scenario 1: What You'll Understand**

After this scenario, you'll have internalized:
- ✅ How requests flow through Spring controllers → services → DAOs
- ✅ Why @Async is needed (don't block HTTP threads)
- ✅ How tenant context travels through the call chain
- ✅ Why cache keys must include tenant ID
- ✅ Concurrency issues when multiple pods write same entity
- ✅ The gap between "request returns" and "operation completes"
- ✅ Why eventual consistency matters

**Not by definition. By reading code.**

---

## PHASE 2: Multi-Pod Coordination (Days 4-6)

### **Scenario 2: Duplicate GenAI Generation Prevention**

Problem: Both Pod A and Pod B try to generate AI summary for same conversation.
- Both call expensive LLM API
- Customer charged twice
- Duplicate summaries in system
- Bad.

**Your job**: Find the code that prevents this. Understand why it works.

---

#### **Code Knowledge Needed**:

```java
🔧 JAVA REFRESHER: Distributed Locks

Normal Java lock:
  synchronized (lock) {
    if (!generated) {
      generateSummary();
    }
  }
  // Works in one JVM. Thread A and B see same lock.

Distributed lock (multiple pods):
  Pod A: redis.set("lock:conv-123", "pod-a", expiry=10s)
  Pod B: redis.set("lock:conv-123", "pod-b", expiry=10s)
  
  Only one succeeds! Other must wait/retry.
  
Why: Redis is shared. All pods talk to same Redis. Real lock.
```

---

#### **Your Task**:
1. Find `SummaryCacheHelper.acquireGenAiSummaryGenerationLock()`
2. Understand the **inverted return**: `false` = you got lock, `true` = someone else has it
3. Trace what happens if you DON'T get the lock (polling code)
4. What happens if lock expires?
5. What happens if pod crashes while holding lock?

---

#### **Production Questions**:
- "Why is the return inverted (opposite of what you'd expect)?"
- "What if Redis is down? System still works?"
- "What if one pod dies while holding lock for 30 seconds?"
- "Is 20-second retry enough? Too much?"

---

## PHASE 3: Cache Invalidation Across Pods (Days 7-9)

### **Scenario 3: One Pod Updates Config, All Pods Get It**

Setup: Config change on Pod A (e.g., change GenAI prompt template).
- Pod A updates MongoDB
- Pod A updates Redis
- Pod A updates local cache
- **Pod B must get this update too**
- **Pod C must get this update too**
- But they're on different machines.

**Problem**: How does Pod B know to refresh its local cache?

**Solution**: Redis Streams (event queue).

---

#### **Code Knowledge Needed**:

```java
🔧 REDIS REFRESHER: Redis Streams

What it is: Kafka-like queue in Redis (simpler than Kafka)

How it works:
  Pod A: stream.publish("config-changes", {"key": "genai-prompt", "version": 2})
  
  Pod B listener: "new message on config-changes!"
         → Receives: {"key": "genai-prompt", "version": 2}
         → Deletes from local cache
         → Next request loads from Redis/Mongo

Why not direct cache invalidation:
  Pod A can't directly tell Pod B "clear your cache"
  (Pods don't know each other's addresses)
  
  But they both listen to Redis Streams
  Pod A publishes, Pod B listens
  Redis handles delivery
```

---

#### **Your Task**:
1. Find `ConfigChangeEventPublisher.publish()`
2. Find `ConfigChangeEventListener` (the subscriber)
3. Trace: When is publisher called? (on every config write?)
4. Trace: When does listener delete local cache?
5. What if listener is slow? (Pod B still has stale data)

---

#### **Production Questions**:
- "Config written to MongoDB at 12:00:00. Pod B local cache expires at 12:00:01. During that 1 second, what does Pod B serve?"
- "What if Redis Streams message is lost? Fallback?"
- "What if Pod C restarts and misses the stream message?"

---

## PHASE 4: Kafka Event Processing (Days 10-12)

### **Scenario 4: Conversation Event Arrives, Must Be Processed Exactly Once**

Kafka delivers: `conversationEventEnvelope` with agent action.
- Must update MongoDB
- Must be idempotent (if delivered twice, still correct)
- Must maintain order per conversation (if event A then B, process in that order)
- Must not lose the event
- Must handle failures gracefully

**Your job**: Understand how this repo guarantees these.

---

#### **Code Knowledge Needed**:

```java
🔧 KAFKA REFRESHER: Consumer Groups & Offsets

What it is: Kafka tracks which messages each consumer group has processed

How it works:
  Consumer group "summary-group" processes partition 0 of "conversation-events"
  
  Message #1000: Process it, return
  Message #1001: Process it, return
  Kafka stores: "summary-group has processed up to offset 1001"
  
  If Pod A crashes:
  New Pod: Kafka says "your group processed up to 1001"
  New Pod: Starts from 1002 (doesn't reprocess)

AT-LEAST-ONCE delivery:
  If acknowledgement fails, Kafka redelivers
  Your code must be IDEMPOTENT (ok to process twice)
```

---

#### **Your Task**:
1. Find `MessageConsumer.conversationEvents()` Bean
2. Trace: When is offset committed? (When does Kafka think message is processed?)
3. Trace: What happens if processing fails? (Does offset advance?)
4. Find: Is there retry logic? Where?
5. Find: Is there dead-letter handling?

---

#### **Production Questions**:
- "Kafka delivers same event twice (network glitch). Your code processes both. What breaks?"
- "Processing takes 2 seconds. Consumer is misconfigured: max.poll.interval.ms = 1s. What happens?"
- "Event processing throws exception. What happens to the offset?"

---

## PHASE 5: Failure Modes & Production Debugging (Days 13-15)

### **Real Production Incidents**

Each day, one realistic incident. You debug it using code reading.

**Incident 1**: "Tenant A reports stale data. They added a note 5 minutes ago, but it's not showing."
- Where could it be stuck? (Kafka queue? Cache? Async write pending?)
- Trace the code to find it.

**Incident 2**: "GenAI API is down. Summaries aren't generating. But system should be resilient?"
- What's the fallback? 
- Find it in code.
- Should it be better?

**Incident 3**: "Pod restarted during graceful shutdown. Lost an in-flight Kafka event."
- What should happen?
- Why didn't it?
- What would you fix?

**Incident 4**: "Cache hit rate dropped from 95% to 40%. Response time increased 5x."
- Why?
- Where would you check first?
- Trace the cache code.

---

## ✅ Completed Phases Checklist

- [ ] **Phase 1** (Days 1-3): One complete flow (add agent note)
  - Understand: Controller → Service → DAO → DB → Cache → Kafka
  - Know: @Autowired, @RequestBody, @Async, CompletableFuture, ThreadLocal, Redis keys
  - Ask: What breaks? What if concurrent? What if tenant missing?

- [ ] **Phase 2** (Days 4-6): Multi-pod coordination
  - Understand: Distributed locks in Redis
  - Know: Why inverted return, why polling, why expiry
  - Ask: What if lock lost? What if Redis down?

- [ ] **Phase 3** (Days 7-9): Cache invalidation
  - Understand: Redis Streams for cross-pod messaging
  - Know: Publisher-listener pattern, eventual consistency
  - Ask: What if stream message lost? What about staleness window?

- [ ] **Phase 4** (Days 10-12): Kafka event processing
  - Understand: Consumer groups, offsets, at-least-once delivery
  - Know: Idempotency required, retry logic, offset commit timing
  - Ask: What if duplicate? What if processing fails?

- [ ] **Phase 5** (Days 13-15): Production debugging
  - Real incidents
  - You debug using code reading
  - Production thinking

---

## 🎓 Code Knowledge Refreshers (As-Needed)

We'll include when relevant:

- **Java Basics**: Generics, interfaces, lambdas, Optional, streams, exceptions
- **Spring Basics**: @Component, @Service, @Repository, @Autowired, @Bean, scopes, lifecycle
- **Async**: CompletableFuture, @Async, thread pools, executors
- **Collections**: Maps, Lists, concurrent collections
- **HTTP**: REST verbs, status codes, headers, response entities
- **Concurrency**: synchronized, volatile, atomic, locks
- **Databases**: Transactions, eventual consistency, queries
- **Messaging**: Topics, consumers, partitions, offsets
- **Caching**: TTL, cache-aside, invalidation, stampede

---

## 🚀 How Each Session Works

```
You:  "I'm ready for Phase 1, Scenario 1"

Me:   "Scenario: Agent adds note. Read the code path. Here are the files."

You:  "I found SummaryRuntimeController. Posted request to addNote() method."

Me:   "Good. What parameters does it extract? Where does @Username come from?"

You:  "It uses @PathVariable for conversationId and @RequestBody for the note.
       @Username must come from... the HTTP header? ContextSetter?"

Me:   "Close. X-Username is from the security filter. ContextSetter uses it.
       Why might the controller overwrite agentId from the request?"

You:  "Security? Prevent impersonation? A client can't claim to be another agent."

Me:   "Exactly. Now trace deeper. Controller calls service.handleAgentNote().
       What does that method do? Find it and read the code."

You:  "[reads code] It gets the summary from DAO, modifies the note, saves it, publishes event."

Me:   "Good. Now production question: When does TenantContext get set? If it's not set,
       what breaks?"

You:  "[traces back] The Kafka consumer sets it. If we call this from HTTP without setting
       it... the DAO would fail trying to access tenantContext.getTenantId()?"

Me:   "Right. Or it'd query MongoDB without tenant filter. What does that mean?"

You:  "Oh. Any user could see any tenant's data!"

Me:   "Exactly. This is why tenant context must be set early. Now trace through the
       full flow again, but this time think about: what if two requests race?"

You:  "[traces] Both load the same SummaryDoc, both modify it, both save it.
       The second save overwrites the first. Data loss!"

Me:   "Right. This repo allows that (no transaction). Is that a bug or intentional?
       Why might you accept this?"

You:  "At scale, locking would slow everything down? And summaries are eventually
       consistent anyway—if both add notes, you get both, just order is uncertain."

Me:   "Good production thinking. Now let's test your understanding. Code review mode:
       If you were adding a feature to delete a note, what would you need to be careful about?"

You:  "[thinks through code] Tenant filtering, async write, cache invalidation,
       Kafka event for other pods..."

Me:   "Right. Now let's trace the code together..."
```

---

## 📌 Key Principles

✅ **Always from code**: Never recite theory. Always show the actual line of code.
✅ **Trace execution**: Follow the path the request/event takes through classes.
✅ **Production thinking**: "What breaks? What if concurrent? What if down?"
✅ **Why not alternatives**: "Why didn't they use X? When would X be better?"
✅ **Build mental models**: Connect code behavior to system behavior to failure modes.

---

**Ready to start Phase 1, Scenario 1: Agent Adds a Note?**

Start here:
```
1. Open: src/main/java/com/uniphore/uassist/summary/controller/SummaryRuntimeController.java
2. Find: The method that handles POST to /notes
3. Tell me: What method name? What does it call? What @annotations?
```

Let's go! 👇
