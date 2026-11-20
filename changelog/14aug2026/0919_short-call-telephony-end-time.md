# Use telephony call start/end time for call-duration calculation

## Overview

`handleConversationStateUpdated` now logs the telephony call end time alongside the event end time on the "Conversation Ended/Finished" event, and threads that telephony end time through to `GenAISummaryService.createGenAiSummary` so it is used specifically for the short-call duration check. `DocConverter.getSummaryDoc` similarly resolves the call-start `SummaryDoc.timestamp` from the telephony-provided `CONVERSATION_STATE_ACTIVE` state instead of the `ConversationStarted` event's own processing timestamp.

## Motivation

The short-call duration calculation previously relied on `conversationEndTime` (processing-time `System.currentTimeMillis()`), which does not reflect the actual telephony call end time. Using the telephony-provided timestamp gives an accurate call duration for the short-call check, without affecting the other uses of `conversationEndTime` (streaming summary event time, GenAI latency metric).

`SummaryDoc.timestamp` (the call-start side of the same duration calculation, via `GenAISummaryService.calculateCallDuration`'s `startTime`) had the identical class of bug: it used the event-processing timestamp rather than the actual telephony call-start time, making it susceptible to the same pipeline-overhead distortion. This closes that gap on the start side as well.

## Changes Made

### SummaryRuntimeServiceImpl
- Added `telephonyCallEndTime`/`eventEndTimeReceived` to the "Conversation Ended/Finished event received" log line.
- Computed `telephonyCallEndTimeMillis` (null-safe) from the `telephonyCallEndTime` protobuf `Timestamp` and passed it into `createGenAiSummary`.
- `handleConversationStartedEvent` computes `isStateV2FfEnabled` (same pattern as `handleConversationStateUpdated`) and passes it to `DocConverter.getSummaryDoc`.

### GenAISummaryService
- Added an overload of `createGenAiSummary` accepting a nullable `shortCallEndTimeMillis`; the original 7-arg method delegates to it with `null` so existing callers are unaffected.
- `isShortCall` now uses `shortCallEndTimeMillis` when present, falling back to `conversationEndTime` otherwise — scoping the telephony end time to the short-call check only.
- Removed the dead `summaryDoc.getMetadata().get("EventEndTime")` fallback inside `isShortCall`: it was never actually populated on any path that reaches this method — the only place that set it constructed a throwaway `SummaryDoc` that was never persisted, and it stored a `Timestamp` instead of the `Long` the filter expected. `calculateCallDuration` now uses `conversationEndTime` (the short-call-specific end time passed in by the caller) directly.

### DocConverter
- Added `resolveCallActiveTimestamp(Conversation, boolean isStateV2FfEnabled)` — looks up the `CONVERSATION_STATE_ACTIVE` entry's timestamp from `getStatesV2List()` (state-v2 FF on) or `getStatesList()` (legacy), falling back to the envelope's own timestamp if no such entry exists.
- `getSummaryDoc(ConversationEventEnvelope, String, boolean)` now takes an `isStateV2FfEnabled` flag and uses the resolved telephony call-start time for `SummaryDoc.timestamp`. The header summary's own timestamp is unchanged (still the event-processing time).

### SummaryRuntimeServiceImplUnitTest
- Added explicit `isFFEnabled(UASSIST_STATE_V2_UPDATE_FF, FF_CONTEXT_KIND) → false` stubs to the tests exercising `handleConversationStartedEvent`, since Mockito's strict stubbing rejects the newly-added, previously-unstubbed FF check once other flags on the same mock are already stubbed.

## Files Added/Modified

- `src/main/java/com/uniphore/uassist/summary/service/SummaryRuntimeServiceImpl.java`
- `src/main/java/com/uniphore/uassist/summary/service/GenAISummaryService.java`
- `src/main/java/com/uniphore/uassist/summary/model/DocConverter.java`
- `src/test/java/com/uniphore/uassist/summary/service/SummaryRuntimeServiceImplUnitTest.java`
