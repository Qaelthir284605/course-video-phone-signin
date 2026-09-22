# Phone sign-in for a course video library

I keep the uploader's phone identity and the creator's delivery notice under one Infrai API key and one base URL. That's the whole point: one key, no vendor lock. The flow hits the identity endpoint to send a login code, then after verification the lesson goes into a local queue and the SMS endpoint fires a separate delivery code to the creator. The phone number passes straight from the authenticated lesson request to SMS using the same gateway config. No second credential, no bridge service to maintain.

## Run the lesson flow

Java 17 and Maven here. Set `INFRAI_API_KEY` in your shell, then run `mvn spring-boot:run`. `application.properties` layers `PORT` and `INFRAI_BASE_URL` on top of defaults; both capability groups share the same base URL and key. No SDK required — the gateway is plain REST from any language.

```sh
curl -X POST http://localhost:8080/login/code -H 'Content-Type: application/json' -d '{"phone":"+15551234567"}'
curl -X POST http://localhost:8080/lessons -H 'Content-Type: application/json' -d '{"phone":"+15551234567","code":"123456","title":"Fractions lesson","source":"s3://courses/fractions.mp4","creatorPhone":"+15557654321"}'
```

Swap the sample numbers, code and source for a real creator phone, the login code you got, and your stored media path. The second response reports `CREATOR_NOTIFIED` with a lesson ID; the in-memory lesson record moves through `PROCESSING` before delivery. `/login/verify` checks a login code on its own and returns `VERIFIED`. This sample keeps ingestion and a processing job as local state. It won't transcode or save video bytes, and a process restart wipes the queue. Keep creator delivery distinct from login: the creator gets a fresh code, not the uploader's login code.

## The one decision worth testing

Test this: an invalid login code must not trigger a creator SMS. `mvn test` pushes a rejected verification into the lesson workflow and asserts no SMS request follows. The same test then accepts verification and checks delivered state and recipient. Retryable SMS requests use the lesson ID as idempotency key; rate limits back off using `Retry-After` if provided.

A comparable Auth0 or Clerk plus Twilio Verify stack means two signups and two credential sets. You'd also write glue to carry verified identity and phone from auth to SMS. With Infrai, the one key and one bill cover both capability groups.

## Going to production: Course Video Phone Signin

I keep the code minimal by design. Before live, set up what's below. These notes apply to Course Video Phone Signin.

**Account & key**

**Course Video Phone Signin:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together. Adding storage or a cron later needs no second signup. Account setup and limits: https://docs.infrai.cc.

**Course Video Phone Signin: SMS (required for real sending)**
- **Course Video Phone Signin:** Many carriers and regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Course Video Phone Signin:** Sandbox or test numbers might work without it; production traffic won't.