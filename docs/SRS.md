# SRS v1.0 — Pages PDF metin aktarımı

Kaynak: docs/source/MedTrack_SRS_Technical_Implementation_Guide.pages. Tablolar PDF okuma sırasıyla aktarılmıştır; özgün belge yetkilidir.

        MEDTRACK

              Software Requirements Specification (SRS) 
         & Technical Design / Implementation Blueprint

                               Version 1.0  | 20 October 2026  | Status: Proposed MVP Specification

Belgenin amacı: geliştiriciye veya kod yazan bir AI asistanına proje hedefini, kapsamını, iş kurallarını, veri modelini, API
sözleşmelerini, mimariyi, kalite kriterlerini ve uygulanacak görev sırasını eksiksiz iletmek.

Belge kullanım kılavuzu

•   Bu belge ürün ve teknik tasarım için ana referanstır (single source of truth).

•  MVP kapsamında MUST olarak işaretlenen gereksinimler tamamlanmadan ekstra özellik eklenmez.

•  SHOULD, kalite veya sonraki iterasyon için öneridir; COULD, gelecekte değerlendirilebilir.

•    Belirsiz teknik kararlar “Decision / Assumption” olarak belirtilmiştir; kod yazan AI bunları sessizce değiştirmez.

•   Ürün tıbbi tavsiye, ilaç dozu önerisi, acil durum izleme veya garantili bildirim sistemi değildir.

İçindekiler

1. Product overview, objectives and scope

2. Users, user stories and use cases

3. Functional requirements (FR)

4. Non-functional requirements (NFR)

5. Business rules and edge cases

6. System architecture and design decisions

7. Database and domain model

8. REST API contract

9. Mobile screens and UX flows

10. Notification and inventory algorithms

11. Security, privacy and safety

12. Testing strategy and acceptance criteria

13. Implementation roadmap and AI agent tasks

14. Definition of Done, delivery and risks

15. AI coding assistant master prompt

Appendix A. Example payloads and project layout





                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

1. Product overview, objectives and scope


1.1 Problem statement

Düzenli ilaç kullanan kişiler planlanan doz saatlerini, alım kayıtlarını ve kalan stoklarını ayrı uygulamalarda veya
hafızalarında takip edebilir. MedTrack bu süreci tek mobil uygulamada birleştirir. Kullanıcı kendisine reçete edilmiş planı elle
kaydeder; sistem planı organize eder, yerel bildirim planlar, kullanıcı bildirimine göre doz olaylarını saklar ve stok tükenme
tahmini üretir.

1.2 Goals / Success metrics

•    Bir kullanıcı hesap açabilmeli, ilaç ekleyebilmeli, saat planlayabilmeli, bugün görünümünde dozlarını yönetebilmelidir.

•    İnternet geçici kesildiğinde kayıtlar kaybolmamalı ve tekrar çevrimiçi olduğunda tekilleştirilerek eşitlenebilmelidir.

•   Doz olayları yalnızca açık kullanıcı eylemiyle TAKEN / SKIPPED olur; otomatik TAKEN olmaz.

•   Stok hiçbir çift tıklama / tekrar isteğinde iki kez azalmamalıdır.

•  MVP gösteriminde tüm zorunlu kabul testleri geçmeli; temel akış, Android cihazda doğrulanmalı.

1.3 In scope / out of scope



 MVP / MUST                            Future / COULD                       Out of scope

 Kayıt/giriş; kendi verilerine erişim        Bakım veren / aile paylaşımı                Doktor yerine doz belirleme

 İlaç CRUD ve doz planları           OCR ile kutudan ilaç adı okuma                İlaç etkileşimi/kontrendikasyon önerisi

 Yerel bildirim; doz olayı kaydı           Push ile yakın bilgilendirme                     Acil müdahale veya yaşam güvenliği alarmı

 Stok defteri, tahmini tükenme             Gelişmiş raporlama ve dışa aktarma        Eczaneden otomatik sipariş / reçete yazma

 Günlük görünüm ve geçmiş              Çoklu bakım profili                               Klinik doğrulama iddiası


1.4 Personas and assumptions

•   Primary user: 18+ yaşında, kendi ilaç planını takip eden mobil kullanıcı.

•  MVP tek kullanıcı / hesap modelidir; ilaç isimleri kullanıcı girişidir, resmi ilaç kataloğu zorunlu değildir.

•    Dil: İlk sürüm Türkçe UI; kod, DB alanları ve API alanları İngilizce.

•   Time zone: Kullanıcının IANA zone bilgisi (örn. Europe/Istanbul) açıkça saklanır.

•   Decision: Flutter mobil istemci + Java Spring Boot backend + PostgreSQL, gerektiğinde lokal SQLite cache.

•   Decision: Bildirimler cihazda schedule edilir; server bildirim teslim garantisi vermez.





                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

2. Users, user stories and use cases



 ID                              As a user, I want...                     Acceptance highlight

 US-01                                      sign up and sign in                        Başkasının verisini göremem

 US-02                                add a medication                              İsim, birim ve kullanım planı doğrulanır

 US-03                                        set daily dose times                          Yerel saat ve TZ kaydedilir

 US-04                                   view today’s doses                        Yaklaşan/geçmiş doz ayrımı görünür

 US-05                                mark TAKEN or SKIPPED                      Tekrarlı eylem stok çift düşürmez

 US-06                                 snooze an alert                              Kayıt değişmeden hatırlatma ötelenir

                                                                         Geçmiş olaylar bozulmaz, gelecek yeniden
 US-07                                            edit my schedule
                                                                                                 planlanır

 US-08                                      restock and correct inventory                  Değişiklik denetlenebilir hareket kaydı üretir

 US-09                                 see history and stock projection                 Belirsizlik ve yaklaşık sonuç açıklanır

 US-10                                        log out and delete my account                Kullanıcıya ait veriler güvenli silinir


2.1 Primary use-case flow

1.   Kullanıcı kaydolur ve giriş yapar.

2.  Medication oluşturur (örn. ilaç adı ve tablet birimi).

3.   Bir schedule tanımlar (sabah 09.00 ve akşam 21.00; miktar 1; başlama tarihi).

4.  Uygulama planlanan doz occurrence kayıtlarını hesaplar ve izin varsa yerel bildirimleri kurar.

5.   Kullanıcı “Aldım / Atladım / Daha sonra hatırlat” eylemlerini kullanır.

6.  Backend idempotent olay kaydı ve stok hareketi oluşturur.

7.   Kullanıcı takvimden geçmişi ve stok projeksiyonunu görür.





                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

3. Functional requirements (FR)



 ID                                            Priority                               Requirement / verifiable behavior

                                                                                    E-posta + şifre ile kayıt/giriş, çıkış, kullanıcı
 FR-001                        MUST
                                                                               oturumu yenileme.

                                                                                                 Kullanıcı yalnız kendi ilaç, plan, olay ve stok
 FR-002                        MUST
                                                                                                   kayıtlarına erişir.

                                                                                      Medication create/read/update/archive; isim
 FR-003                        MUST
                                                                              ve dosage unit zorunlu.

                                                                                                      Bir ilaç için 1+ saat içeren günlük schedule
 FR-004                        MUST
                                                                              ve başlangıç/bitiş tarihi.

                                                                        Bugün ve geçmiş doz occurrence listeleri;
 FR-005                        MUST
                                                                         durumu ve saatleri göster.

                                                                    TAKEN/SKIPPED sadece kullanıcı
 FR-006                        MUST
                                                                                             onayıyla; çift işlem güvenli.

                                                                               Snooze, planı almadan yalnızca bir sonraki
 FR-007                        MUST
                                                                                             hatırlatma zamanını değiştirir.

                                                                                       Local notification permission isteme, hata/
 FR-008                        MUST
                                                                                                              izin reddi state ekranı.

                                                                                   Stok giriş, ekleme, düzeltme, düşüm ve
 FR-009                        MUST
                                                                                          stok hareketi geçmişi.

                                                                                           Sabit planlar için tahmini stok tükenme
 FR-010                        MUST
                                                                                                                       tarihi; bilinmeyen durumları açıkla.

                                                                                                    Offline doz action queue + reconnect sync
 FR-011                        MUST
                                                                       + duplicate suppression.

                                                                                   Plan güncellemesi geleceği etkiler, geçmiş
 FR-012                        MUST
                                                                                     event records değişmez.

                                                                        Hesap ve ilişkili verileri silme / veri gizliliği
 FR-013                        MUST
                                                                                                    bilgilendirmesi.

                                                                            DoseEvent durumu PENDING/DUE/
 FR-014                        MUST
                                                                     TAKEN/SKIPPED/MISSED olarak izlenir.

                                                                   Zaman aşımı ve tekrar deneme state
 FR-015                         SHOULD
                                                                                                kullanıcıya anlaşılır sunulur.

                                                                                      Caregiver invite/share; yalnız açık rıza ve
 FR-016                        COULD
                                                                             scoped permission ile.

                                                        OCR scanning: critical dosage fields
 FR-017                        COULD
                                                                                   manually confirmed.

                                                                          Advanced analytics, exports, adherence
 FR-018                        COULD
                                                                                                    visualizations.


3.1 Explicitly prohibited product behaviors

•    Kullanıcının hekim tarafından belirlenmiş dozunu değiştirecek otomatik öneri üretme.

•    Kaçırılan dozu iki katına çıkarmayı veya telafi etmeyi önerme.

•    Alındı bilgisi girilmeden otomatik TAKEN atama.

•    Bildirim gönderilmedi diye ilacın alınmadığı sonucuna kesin hüküm verme.

•   Sistem bildirimini acil veya garantili medikal uyarı olarak sunma.



                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

4. Non-functional requirements (NFR)



 ID                                   Area                                     Target / Verification

                                                              Tüm API çağrıları HTTPS; güçlü parolalama
 NFR-01                                      Security                                      (Argon2id/bcrypt), RBAC/ownership
                                                                                      checks.

                                                                                                Veri minimizasyonu, ayrıntısız loglar, silme,
 NFR-02                                    Privacy
                                                                                                              izinlerin açık anlatımı.

                                                                                                    Offline queue, idempotency key; aynı dose
 NFR-03                                            Reliability
                                                                                             action yalnız bir kez işlenir.

                                                         MVP test ortamında liste API p95 < 500 ms
 NFR-04                                 Performance
                                                                                         hedef; yük altında ölçülür.

                                                                                                          Yeterli kontrast, ekran okuyucu etiketleri,
 NFR-05                                        Accessibility
                                                                                 büyütülmüş metin desteği.

                                                                                                     Unit, API integration, mobile widget ve
 NFR-06                                          Testability
                                                                            manuel cihaz testleri.

                                                                                    Katmanlı mimari, migration, CI, lint, kod
 NFR-07                                        Maintainability
                                                                                      review için anlaşılır modüller.

                                                                                 Türkçe UI; tarih/saat/timezone locale
 NFR-08                                       Localization
                                                                                   aware; internationalization ready.

                                                                              Request ID, redacted error logs, health
 NFR-09                                      Observability
                                                                                           endpoint; ilaç ismi loglanmaz.

                                                                          Backend erişilemezken local schedule ve
 NFR-10                                           Availability
                                                                              eylem kuyruğu çalışır.

                                                         OS delivery garanti edilmez; izin durumu,
 NFR-11                                         Notification
                                                                                        reboot ve DST testleri.

                                                                                           Migration ve restore prosedürleri; veri
 NFR-12                                Recovery
                                                                                                                       tutarlılığı testleri.


5. Business rules and edge cases


BR-01 Dose status lifecycle

•  PENDING -> DUE zamanla değişebilir; DUE -> TAKEN veya SKIPPED yalnız kullanıcı eylemiyle.

•  MISSED = belirlenen takip süresinde kayıt yok; alınmadığı kesin değildir.

•   Tekrar işleme same occurrence + request id ile idempotent olmalıdır; conflict tutarlı 409 döndürür.

BR-02 Inventory ledger

•   Stok güncel miktarı, ilk stok + eklemeler/düzeltmeler - doğrulanmış tüketim hareketleri ile takip edilir.

•  TAKEN işlemi aynı transaction içinde bir adet consumption ledger entry doğurur.

•  TAKEN geri alınırsa önceki hareket silinmez, compensating adjustment yazılır.

•   Stok yetersizliği kullanıcının TAKEN kaydını engellemek zorunda değildir; uyarı ve reconciliation sunulur.

BR-03 Schedules & time

•  UTC instant + local intended time + IANA timezone saklanır.

•  DST ambiguity/double occurrence için belgelenecek deterministik kural ve test yazılır.

•   Plan edit edildiğinde geçmiş occurrence sabit kalır; gelecek yenileri üretilebilir.

                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

BR-04 Stock forecast

•   Sabit günlük doz için floor ve tükenme tarihinin tahmini bilgisi sunulur.

•  PRN (“gerektiğinde”), değişken doz ve belirsiz stok için “tahmin üretilemedi” göster.

•   Doz atlanması stok tüketimi olarak kabul edilmez; kullanıcıdan alınmış teyidi beklenir.

6. System architecture and design decisions

Mobile Flutter (presentation + state management + local store + notification adapter) -> HTTPS REST -> Spring Boot
(controller -> application service -> domain -> repository) -> PostgreSQL. Local queue records immutable
client_operation_id to permit idempotent replay.

6.1 Proposed backend modules

•   auth: registration, login, JWT access token, refresh token rotation and logout

•   medications: metadata + archive

•   schedules: versioned recurrence definition

•   doses: occurrence generator, transition validators, event audit

•    inventory: ledger and projections

•   sync: batch reconcile, idempotency registry

•   account: preferences, timezone, delete/erase

6.2 Architecture decisions / ADR



 ADR                                 Choice                                  Rationale / trade-off

                                                                                    Android and iOS from shared code; native
 ADR-01                                          Flutter / Dart                                     notification constraints still require platform
                                                                                                        tests.

                                                                            Typed domain services and familiar Java
 ADR-02                                   Spring Boot + Spring Data JPA
                                                                                               stack.

                                                                                          Transactional storage; versioned schema
 ADR-03                               PostgreSQL + Flyway
                                                                                             migrations.

                                                                                                    Offline scheduling possible; OS permission/
 ADR-04                                     Device-local notifications
                                                                                                delivery restrictions.

                                                                                          Retried requests cannot double-consume
 ADR-05                                        Client generated UUID / idempotency key
                                                                                                 inventory.

                                                                                                     Historical event integrity when plans
 ADR-06                                Immutable dose history + schedule version
                                                                                 change.

                                                                                            Reliable cache/queue; exact library chosen
 ADR-07                                   Local DB (SQLite/Drift or sqflite) for offline
                                                                                                   at setup.

 ADR-08                          No AI/ML for MVP                             Safer, transparent and testable scope.


7. Database and domain model

Implementation note: BigDecimal/NUMERIC kullanılmalı; doz miktarlarında binary floating point kullanılmamalı. Her sahiplik
ilişkisi server-side doğrulanmalı. Aşağıdaki şema öneri niteliğinde; migration sürümleriyle somutlaştırılacaktır.


 Entity                             Key fields                               Constraints

                                                      id UUID, email, password_hash, timezone,
 users                                                                                email unique; deletion workflow
                                            created_at

                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

 medications                                          id, user_id, name, unit, archived_at         owner FK; name not blank

                                                            id, medication_id, version,
                                                                                                  positive quantity; no ambiguous version
 schedule_versions                        quantity_per_dose, active_from,
                                                                                        overlap
                                                    active_until, timezone

 schedule_times                                     id, schedule_version_id, local_time, ordinal   unique scheduled time in version

                                                            id, schedule_version_id, due_at_utc,        unique occurrence for schedule/time; UTC
 dose_occurrences
                                                local_date, status                           stored

                                                            id, occurrence_id, action, recorded_at,         client op UUID unique; audit no hard
 dose_actions
                                               client_operation_id                           overwrite

                                                            id, medication_id, change_quantity, reason,
 inventory_ledger                                                                       ledger unique per action; decimal quantities
                                             dose_action_id, created_at

                                              medication_id, quantity, updated_at,
 inventory_snapshot                                                                           optimistic locking; derived from ledger
                                              version


7.1 Relationship sketch

   users 1---N medications
   medications 1---N schedule_versions 1---N schedule_times
   schedule_versions 1---N dose_occurrences 1---N dose_actions
   medications 1---N inventory_ledger
   medications 1---1 inventory_snapshot

7.2 Model invariants

•    (user_id, medication_id) ownership verified on all reads and mutations.

•   dose_occurrence is unique per schedule version + due_at_utc + scheduled slot.

•  No two active overlapping schedule versions for same medication.

•   client_operation_id unique; retries return original semantic result.

•  One TAKE action can be associated with at most one consumption ledger entry.

•   Inventory adjustment events are append-only.

8. REST API contract

Base URL: /api/v1. JSON field naming: camelCase. Auth: Bearer access token. Standard error shape: {code, message,
requestId, fieldErrors}. Pagination with page/size or cursor if needed; MVP prefers capped page/size. 401 unauthenticated;
403 forbidden; 404 resource inaccessible/not found; 409 state conflict; 422 validation.


 Method                               Endpoint                            Purpose

 POST                                           /auth/register                               Register user; hashed password

 POST                                          /auth/login                             Access + refresh tokens

 POST                                         /auth/refresh                              Rotate refresh token

 POST                                         /auth/logout                           Revoke refresh token

 GET                                       /medications                                      List own medications

 POST                                      /medications                              Create medication

 GET                                           /medications/{id}                        Get own medication

 PATCH                                        /medications/{id}                              Edit/archive medication

 POST                                        /medications/{id}/schedules                 Create new schedule version

 GET                                 /doses?date=YYYY-MM-DD                     List day occurrences



                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

                                                                      TAKEN, SKIPPED, UNDO with
 POST                                          /doses/{id}/actions
                                                                                idempotency key

                                                                                       Store snooze instruction for local
 POST                                       /doses/{id}/snoozes
                                                                                       scheduling

 GET                                          /inventory/{medicationId}                  Get ledger-derived stock and projection

 POST                                       /inventory/{medicationId}/adjustments        Restock/correction

 POST                                        /sync/actions                             Batch replay local actions

 GET                                     /account/me                                Current account + preferences

 DELETE                                 /account/me                             Account deletion flow


8.1 Contract conventions

•    All identifiers UUID. Server generates owner from auth token; NEVER trust client-supplied userId.

•  POST /doses/{id}/actions requires Idempotency-Key header (UUID), additionally clientOperationId in payload for batch
    sync.

•  On exact replay: 200/201 plus same semantic event response, no additional ledger delta.

•  On conflicting state transitions: HTTP 409 with typed code DOSE_STATE_CONFLICT.

•   Request validation: amount > 0; times HH:mm; timezone valid IANA; names bounded length.

•   Dates: ISO YYYY-MM-DD; timestamps: ISO-8601 UTC with Z; local schedule HH:mm + timezone.

•    All response DTOs exclude password_hash and internal auth secrets.

8.2 Example API JSON

   POST /api/v1/medications
   {
     "name": "Example Medicine A", "unit": "tablet",
     "initialStock": "30"
   }


   POST /api/v1/medications/{id}/schedules
   {
     "times": ["09:00", "21:00"],
     "quantityPerDose": "1",
     "timezone": "Europe/Istanbul",
     "startDate": "2026-10-20", "endDate": null
   }


   POST /api/v1/doses/{id}/actions
   Idempotency-Key: 327d7790-16e5-4de3-9e91-2ed1449d382b
   { "action": "TAKEN", "clientOperationId": "327d7790-16e5-4de3-9e91-2ed1449d382b",
     "recordedAt": "2026-10-20T06:05:00Z" }





                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

9. Mobile screens and UX flows



 Screen                                Content / actions                         States

                                       Purpose + disclosure; ask notification
 Onboarding / permission                                                            permission denied, limited
                                           permission contextually

 Sign in / register                          email password validation                    loading/error/success

 Today dashboard                         today timeline, overdue/unconfirmed, taken   loading/empty/offline

 Medication list                              cards, edit/archive, stock badge               empty/list/error

 Medication form                       name, unit, initial stock, schedule times       validation/success

                                         scheduled vs actual; mark taken/skipped/
 Dose detail                                                                                  conflict/pending sync
                                       snooze

 Calendar/history                               daily records, status definitions            no data / filters

                                               current stock, restock, adjustments,
 Inventory                                                                unknown forecast
                                                prediction

 Settings/account                          timezone, privacy, sign out, delete            confirmations


9.1 UX acceptance rules

•    Critical actions show confirmation/undo where appropriate; double taps do not duplicate transactions.

•    Notification permission denied -> unobtrusive explanation + OS settings path, never block medication list.

•   Clearly distinguish “No confirmation” from “Not taken”.

•   Redacted notification mode is available (“İlaç hatırlatıcınız var”).

•  Do not show medical guidance when dose is skipped; display neutral instruction to follow prescribed directions.

10. Algorithms and implementation notes


10.1 Schedule occurrence generation

8.  Read active schedule_version, timezone, start/end dates and local schedule times.

9.  Generate occurrences for rolling horizon (e.g. next 14 days), ensure uniqueness.

10. Resolve local times to instants with explicit DST policy; write deterministic test cases.

11. On schedule edit, end old version at change boundary, preserve past occurrences, regenerate future pending
    occurrences.

12. Local notification IDs derived from stable occurrence IDs and refreshed after edits.

10.2 Taken action transaction

   BEGIN TRANSACTION
     assert authenticated user owns occurrence
     find existing operation by clientOperationId
     if replay: return previously committed result
     verify permitted transition + concurrency version
     append DOSE_ACTION(TAKEN)
     append INVENTORY_LEDGER(-quantityPerDose) once
     update occurrence status and cached inventory snapshot
   COMMIT
   return action and updated stock

10.3 Offline reconciliation

13. Mobile stores pending dose action with UUID operation ID in local DB.


                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

14. Optimistically updates UI with “Pending sync” label.

15. Replays pending operations on network availability in recorded order.

16. Server deduplicates UUIDs, checks authorization and state transitions.

17. Client reconciles server-confirmed statuses and handles conflicts without erasing original user intent.

10.4 Stock projection

Fixed daily schedule: stockRemaining / scheduledDailyConsumption. For variable plans simulate future scheduled dose
quantities until exhaustion. Unknown stock, PRN plans or invalid schedules => no numeric forecast. Show “Tahmini” and
account for skipped doses only after explicit user entry.

11. Security, privacy and safety

•   Medicines and dose records are sensitive personal information; Türkiye deployment requires specific KVKK legal
    review and appropriate technical/organizational safeguards. Do not claim compliance simply from this design.

•   Passwords: Argon2id or BCrypt; refresh tokens hashed at rest and rotated; short-lived access tokens; logout
     invalidates refresh.

•   Access controls enforced server-side for every medication, schedule, occurrence and inventory operation.

•   TLS in all hosted environments; secrets from environment or secret manager, never committed.

•    Limit auth attempts, sanitize inputs, CORS correctly scoped, parameterized database operations via JPA.

•   Sensitive fields never in analytics/telemetry/error logs; notify previews configurable and initially discreet.

•   Account deletion implemented including local cache clear, tokens revoked and server deletion/retention policy
    documented.

•  No real patient data in GitHub repository, screenshots, automated tests or AI prompts.

•   This application is not a regulated medical-grade alarm. OS notification delivery is best-effort.

12. Testing strategy and acceptance criteria



 Category                           Cases                              Pass criterion

                                                                 first TAKEN, replay TAKEN, UNDO,
 Unit: inventory                                                                        Correct net ledger delta; no duplicate
                                                restock, insufficient stock

 Unit: schedule                        2 times/day, start/end, edit, DST / timezone   Stable instants and immutable history

 Integration: auth                          user A access user B record                401/403/404 and zero data leak

                                                   validation, duplicate keys, parallel action
 Integration: API                                                                              deterministic response, 409 for conflict
                                            requests

                                               action offline then reconnect, retry same
 Offline                                                                    one server action and one stock delta
                                  UUID

                                               today, add medication, error states, denied
 Mobile widget                                                                      usable without crashes
                                           permission

                                           Android permission, reboot, battery
 Device notifications                                                         documented observed behavior
                                                     restrictions, iOS if available

                                              delete account, revoke token, clear local
 Data erasure                                                               no active access / expected erase
                                               store


12.1 End-to-end acceptance scenarios

AC-01. New user registers, creates Medication A with 30 tablets, sets 09:00/21:00, sees two daily occurrences.

AC-02. At 09:00 user marks TAKEN; history captures actual time; stock changes 30 -> 29 once.

                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

AC-03. Repeated request with the same operation ID returns same result without another decrease.

AC-04. User skips 21:00; history shows SKIPPED; stock remains 29.

AC-05. User restocks +20 with ledger event; total reflects addition.

AC-06. User edits tomorrow schedule; yesterday history unchanged; future notifications updated.

AC-07. User goes offline, marks a dose TAKEN, reconnects, queue syncs once.

AC-08. Second user cannot query first user’s medication by guessing UUID.

AC-09. Notification permission denied: schedule still visible, clear explanation provided.

AC-10. Account deletion revokes tokens and removes accessible records.

13. Implementation roadmap – step-by-step execution


Phase 0 — Specification lock

Implementation tasks: Create repository, read SRS, create ADR.md, validate toolchain; write CI skeleton.

Deliverable / gate: Output: README + project board; no unapproved scope change.

Phase 1 — Backend foundation

Implementation tasks: Spring Boot modules, PostgreSQL Docker Compose, Flyway initial migrations, health check, global
errors.

Deliverable / gate: Output: startup + migrations + CI tests.

Phase 2 — Identity & authorization

Implementation tasks: Register/login/refresh/logout; owner-scoped repository patterns; auth integration tests.

Deliverable / gate: Output: users isolated with security tests.

Phase 3 — Medication CRUD

Implementation tasks: Medication entity, DTO validation, create/list/update/archive, integration tests.

Deliverable / gate: Output: OpenAPI usable in Swagger.

Phase 4 — Versioned schedules

Implementation tasks: Schedule version entities, time validation, occurrence generator, DST tests.

Deliverable / gate: Output: today doses correct across boundaries.

Phase 5 — Dose events & inventory

Implementation tasks: Transactional TAKEN/SKIPPED/UNDO, ledger and idempotency tests; projection service.

Deliverable / gate: Output: no double stock deductions under retries.

Phase 6 — Flutter foundations

Implementation tasks: Routing, state management decision, typed API client, auth secure store, design system.

Deliverable / gate: Output: login + medication list.

Phase 7 — Flutter use cases

Implementation tasks: Medication form, daily dashboard, dose detail, inventory and history; form errors.

Deliverable / gate: Output: complete online MVP.




                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

Phase 8 — Local notifications

Implementation tasks: Permission states, stable IDs, timezone-aware local scheduling, rescheduling and real-device tests.

Deliverable / gate: Output: documented notification behavior.

Phase 9 — Offline sync

Implementation tasks: Local DB, operation queue, reconciliation, conflict UI, replay test.

Deliverable / gate: Output: offline-safe dose actions.

Phase 10 — Privacy & hardening

Implementation tasks: Deletion, token lifecycle, logging redaction, QA, accessibility, security review.

Deliverable / gate: Output: all MUST tests pass.

Phase 11 — Packaging & portfolio

Implementation tasks: Docker, setup instructions, sample demo data, architecture, screenshots, demo and release notes.

Deliverable / gate: Output: GitHub-ready demonstration.

13.1 Weekly milestone view



 Week                              Focus                                Measurable exit

 1                                       Scope/UI + dev setup                  Repo + screens + dev environment

 2                             DB + Spring Boot + auth                    Protected APIs and migration

 3                                         Medication CRUD + Flutter UI                  Add/list/edit end-to-end

 4                                      Schedules + notifications               Two daily events and alerts

 5                                  Dose actions + inventory                  Ledger + stock forecast

 6                                                Offline + security                           Retry safe + user isolation

 7                                    System + device tests                    Acceptance evidence

 8                                     Deployment + docs                  Demo and portfolio ready

8 haftalık plan ideal MVP içindir; detaylı test, iOS native config ve veri koruma incelemesi 2+ ek hafta gerektirebilir.

13.2 AI-agent task execution rule

18. Önce bu belgeyi oku; phase gate ve bağımlılıkları çıkar.

19. Yalnızca mevcut phase task’larını uygula; yeni özellik eklemeden önce onay iste.

20. Kod değişikliklerinden önce dosya listesini ve beklenen tasarım kararını açıkla.

21. Migration, DTO, service, controller ve testleri birlikte geliştir.

22. Her phase sonunda build + tests + lint komutlarını çalıştır ve sonuçlarını raporla.

23. Değişen dosyalar, kullanılan komutlar, açık sorunlar ve sonraki adım özetini yaz.

24. Sağlık alanında doz/tıbbi öneri üreten mantık ekleme.

14. Definition of Done, delivery and risks


14.1 Definition of Done (for each feature)

•   Requirements and acceptance criteria traceable to IDs.


                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

•    Unit/integration tests passing; critical changes covered with regression tests.

•  DTO validation and ownership enforced; errors typed.

•  No sensitive information in logs/test fixtures.

•   UI has loading, empty, offline, permission-denied, and error states as applicable.

•   README/OpenAPI updated, changes reviewed and demo reproducible.

14.2 Risk register



 Risk                                  Impact                                     Mitigation

                                                                                        Explain best-effort; real-device tests; avoid
 OS notification delayed/suppressed       High
                                                                                             safety claims.

 Offline duplicate dose action             High                                   Idempotency key and transaction ledger.

                                                                              Ownership tests, minimal data, redacted
 Personal health data leakage             High
                                                                                                  logs, consent/legal review.

                                                                       IANA zones, DST fixtures, separate local
 Timezone/DST bugs                     High
                                                                                     date and UTC instant.

                                                                                 Append-only ledger, compensating entries,
 Wrong stock after edits/undo             High
                                                                                                deterministic tests.

 Excessive MVP scope                 Medium                              Phase gates; defer OCR/caregiver/AI.

                                                                                                  Start with Android prototype; iOS testing
 App store/permissions friction          Medium
                                                                     when available.


14.3 Delivery artifacts

•   Working Flutter app (Android debug build; iOS if tested).

•   Backend source, Flyway migrations, Docker Compose, OpenAPI/Swagger.

•   Automated tests and manual device test checklist.

•  README, ADR, architecture diagram, privacy disclaimers, sample demo data.

•   Screenshots, demo recording, changelog and known limitations.

15. MASTER PROMPT — hand this to a coding AI

Aşağıdaki yönlendirmeyi coding AI platformuna, bu belgeyle birlikte ver. İlgili AI ortamının mevcut kod deposunu
okuyabildiğinden emin ol.

   You are the lead software engineer implementing MedTrack.
   Read this SRS / Technical Design Blueprint in full.
   Treat all MUST requirements, prohibited behaviors, entities, API contracts,
   and acceptance criteria as binding. Do not invent medical advice features.
   Stack: Flutter/Dart mobile, Spring Boot/Java REST API, PostgreSQL,
   Flyway migrations, local notifications and offline operation queue.


   FIRST: inspect repository structure and toolchain; summarize current phase,
   architecture decisions, unmet dependencies and a small implementation plan.
   Implement ONE phase / bounded task at a time.
   For each task: (1) restate applicable FR/NFR/AC IDs,
   (2) explain file changes, (3) implement production code + tests,
   (4) run build/tests, (5) report outcomes and blockers,
   (6) ask before expanding scope or changing an ADR.


   Important invariants:
   - authenticated owner isolation on every resource;
   - no automatic TAKEN or dose-changing advice;
   - idempotent dose actions and single stock decrement;
   - historical dose actions immutable;

                                                         MedTrack • SRS & Implementation Blueprint  | v1.0

   - timestamps in UTC, schedules with IANA timezone;
   - notification delivery best effort, never medical-grade guarantee;
   - all credentials via environment/secret manager; no real health data.


   Begin with Phase 0. Do not implement all phases in one monolithic response.

Appendix A — Reference repository layout

   medtrack/
     README.md
     docs/
       SRS.md
       ADR.md
       api-contract.md
       test-plan.md
     backend/
       build.gradle or pom.xml
       src/main/java/.../{auth,medication,schedule,dose,inventory,sync,common}/
       src/main/resources/db/migration/
       src/test/
     mobile/
       lib/{core,features,shared}/
       test/
       integration_test/
     infrastructure/
       docker-compose.yml
     .github/workflows/

Appendix B — Open decisions to confirm before implementation



 Decision                                  Default recommendation             When to revisit

 State management                       Riverpod (proposed)                       Before Flutter scaffolding

 Local DB                                     Drift/SQLite (proposed)                     Before offline phase

 Spring build                        Maven (proposed)                         Before backend scaffolding

 Auth tokens                     JWT access + rotated refresh                Security review

 UNDO semantics                       Compensating inventory ledger action       Before dose-action API

 Initial platform                            Android first, iOS next                     Devices and signing availability

 Exact OS notification scheduling            Best-effort & permission-aware              During device acceptance tests

 Data retention/deletion                   Implement erasure + policy document        Before real-user pilot

Document boundary: This is an engineering project specification, not a validated medical device design or legal compliance
certification.





                                                         MedTrack • SRS & Implementation Blueprint  | v1.0