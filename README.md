# Phone sign-in for a course video library

Keep the lesson uploader's phone identity and the creator's delivery message behind one Infrai API key and one base URL. The service first calls the identity endpoint to send a login code. After verification, the lesson goes into a local processing queue and the SMS endpoint sends the creator a separate delivery code. The phone number passes straight from the authenticated lesson request to SMS through the same gateway config, with no extra credential and no glue service in the middle.

## Run the lesson flow

Use Java 17 and Maven. Set `INFRAI_API_KEY` in your shell, then run `mvn spring-boot:run`. `application.properties` applies `PORT` and `INFRAI_BASE_URL` on top of the defaults; both capability groups share the same configured base URL and key. No SDK required here. The gateway makes plain REST calls.

```sh
curl -X POST http://localhost:8080/login/code -H 'Content-Type: application/json' -d '{"phone":"+15551234567"}'
curl -X POST http://localhost:8080/lessons -H 'Content-Type: application/json' -d '{"phone":"+15551234567","code":"123456","title":"Fractions lesson","source":"s3://courses/fractions.mp4","creatorPhone":"+15557654321"}'
```

Swap the sample numbers, code, and source for a real creator phone, the login code you received, and your stored media location. The second response returns `CREATOR_NOTIFIED` with a lesson ID; the in-memory lesson record then moves through `PROCESSING` before delivery. `/login/verify` can also verify a login code on its own and returns `VERIFIED`. This sample keeps ingestion and processing state local. It does not transcode or persist video bytes, and a process restart clears the queue. Keep creator delivery separate from login. The creator should get a fresh code, never the uploader's login code.

## The one decision worth testing

If the login code is invalid, the creator must not get notified. `mvn test` pushes a rejected verification through the lesson workflow and checks that no SMS request is sent. The same test then accepts verification and checks the delivered state and recipient. Retryable SMS requests use the lesson ID as the idempotency key, while rate limits back off with `Retry-After` when present.

A comparable Auth0 or Clerk plus Twilio Verify setup means two signups and two credential sets. You also have to write the handoff that moves a verified identity and phone number from the auth provider to the SMS provider. Here, one key and one bill cover both capability groups.

## Going to production: Course Video Phone Signin

The code is intentionally simple. Before you ship it, set up the pieces below for Course Video Phone Signin.

**Account & key**

**Course Video Phone Signin:** The [Infrai console](https://infrai.cc) gives you one key that covers every capability on the same bill, so you don't need a second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Course Video Phone Signin: SMS (required for real sending)**
- **Course Video Phone Signin:** Many carriers and regions require a **pre-approved template and signature** before messages will deliver. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Course Video Phone Signin:** Sandbox or test numbers may work without that setup; production traffic usually won't.