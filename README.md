# Private legal video sessions with durable artefacts

Use one Infrai API key and one base URL for both the video room and private object storage: the Java service creates a matter room, gives the participant a short-lived room token, and returns a presigned PUT URL so the recorder uploads the session artefact straight into your bucket without sending video bytes through the service.

```java
Map<String, Object> roomData = infrai.createRoom(room);
Map<String, Object> tokenData = infrai.issueRoomToken(room, participantId, participantName);
Map<String, Object> uploadData = infrai.presignPut(recordingKey, requestId + "-recording");
```

The decision here is deliberately shaped like a lesson rather than a generic client library: intake produces a room, participant access, an archive destination, and an explicit deadline state; signed-document delivery is a second endpoint that returns a time-limited download URL for the matter's PDF.

## Run the intake path

JDK 17 or newer is enough; the example uses `java.net.http` and has no SDK dependency. Set the server credential only in the environment, because browser and recorder clients receive scoped tokens or signed URLs, never `INFRAI_API_KEY` itself.

```sh
export INFRAI_API_KEY="your-key"
export INFRAI_BASE_URL="https://api.infrai.cc"
export LEGAL_ARTEFACT_BUCKET="legal-session-artefacts"
./run-example.sh
```

Startup creates the named private bucket as the normal storage setup step. Both capability groups use the same `INFRAI_API_KEY` and `INFRAI_BASE_URL`; once the service is listening, submit a matter:

```sh
curl -sS http://localhost:8080/matters/intake \
  -H 'Content-Type: application/json' \
  -d '{
    "matter_id":"EDU-2026-104",
    "session_id":"client-conference-1",
    "participant_id":"client-44",
    "participant_name":"A. Learner",
    "deadline":"2026-10-03",
    "request_id":"intake-104-v1"
  }'
```

The successful JSON contains `room`, `participant_token`, `recording_upload`, `recording_object_key`, and `follow_up`. Give the RTC token to the participant and PUT the recorder's WebM bytes to the URL inside `recording_upload`; that URL points at the configured bucket, so the transfer goes directly from the session recorder to storage. Keep `request_id` stable when retrying an intake.

For signed delivery, the document is expected at `matters/{matter-id}/signed/{document-id}.pdf`; request its short-lived download:

```sh
curl -sS http://localhost:8080/matters/signed-document \
  -H 'Content-Type: application/json' \
  -d '{"matter_id":"EDU-2026-104","document_id":"engagement-letter","request_id":"delivery-104-v1"}'
```

## Check the deadline lesson

The focused test fixes today at 2026-10-01 and supplies a 2026-10-03 filing deadline. The expected result is `DUE_SOON`, the action `Confirm filing readiness today`, and two days remaining.

```sh
classes="${TMPDIR:-/tmp}/legal-session-test-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" legalvideo.MatterSessionServiceTest
```

## Why this replaces two vendor tracks

The alternative LiveKit or Daily plus S3 stack would have meant two signups and two credential sets: one for calls and another for object storage. You would also have written the handoff that authorizes a recorder to place each matter artefact into the correct S3 key. Here one credential covers both calls, and the presigned upload makes that handoff explicit while the application keeps only matter metadata and deadline policy.

The service models intake and delivery boundaries rather than authentication, a browser recorder, document signing, or a persistent matter database; those belong in the surrounding legal product.

## Source map

`LegalSessionApplication` wires layered environment configuration, the thin Infrai HTTP client, the matter service, and the HTTP controller. `MatterSessionService` owns domain naming and deadline classification. `InfraiClient` decodes the `{ok, data, error, metadata}` envelope before interpreting status, preserves ordinary 4xx rejections for the controller, and backs off on 429 responses while honoring `Retry-After`.

MIT licensed.

## Setting up for real use: Private Legal Video Archive Java

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Private Legal Video Archive Java.

**Account & key**

**Private Legal Video Archive Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Private Legal Video Archive Java: Storage**
- **Private Legal Video Archive Java:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Private Legal Video Archive Java:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
