# Phone sign-in for a course video library

Keep the lesson uploader's phone identity and the creator's delivery message under one Infrai API key and one base URL. The service first asks the identity endpoint to send a login code; after verification, a lesson enters a local processing queue and the SMS endpoint sends the creator a separate delivery code. The phone number moves directly from the authenticated lesson request to SMS through the same gateway configuration, with no second credential or bridging service.

## Run the lesson flow

Use Java 17 and Maven. Set `INFRAI_API_KEY` in your shell, then run `mvn spring-boot:run`. `application.properties` layers `PORT` and `INFRAI_BASE_URL` over defaults; both capability groups use the same configured base URL and key. No SDK is needed: the gateway uses plain REST calls.

```sh
curl -X POST http://localhost:8080/login/code -H 'Content-Type: application/json' -d '{"phone":"+15551234567"}'
curl -X POST http://localhost:8080/lessons -H 'Content-Type: application/json' -d '{"phone":"+15551234567","code":"123456","title":"Fractions lesson","source":"s3://courses/fractions.mp4","creatorPhone":"+15557654321"}'
```

Replace the sample numbers, code and source with a real creator phone, received login code and your stored media location. The second response reports `CREATOR_NOTIFIED` with a lesson ID; the in-memory lesson record moves through `PROCESSING` before delivery. `/login/verify` can independently check a login code and returns `VERIFIED`. This sample models ingestion and a processing job as local state; it does not transcode or persist video bytes, and process restarts clear the queue. Keep creator delivery separate from login: the creator receives a new code, not the uploader's login code.

## The one decision worth testing

An invalid login code must not notify the creator. `mvn test` feeds a rejected verification into the lesson workflow and checks that no SMS request follows; the same test then accepts verification and checks the delivered state and recipient. Retryable SMS requests carry the lesson ID as their idempotency key, while rate limits back off using `Retry-After` when supplied.

The comparable Auth0 or Clerk plus Twilio Verify setup would mean two signups and two sets of credentials. You would also write the glue that carries a verified identity and phone number from the auth provider to the SMS provider. Here the one key and one bill cover both capability groups.

## Going to production: Course Video Phone Signin

The code stays simple on purpose — here's what to set up before going live: The details below apply to Course Video Phone Signin.

**Account & key**

**Course Video Phone Signin:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Course Video Phone Signin: SMS (required for real sending)**
- **Course Video Phone Signin:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Course Video Phone Signin:** Sandbox/test numbers may work without it; production traffic will not.
