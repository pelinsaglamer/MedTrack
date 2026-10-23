# API sözleşmesi — Phase 1–8

Kaynak: SRS §8. Domain API tabanı `/api/v1`; auth/account ve ilaç endpointleri aşağıdadır; schedule ve günlük doz endpointleri aşağıdadır; doz işlemleri, inventory ve haftalık özet aşağıdadır.

Mevcut operasyonel endpoint: `GET /actuator/health`, DB erişilebilirken `200 {"status":"UP"}`; erişilemezken 503. Bileşen ayrıntıları kapalıdır.

Her istekte sunucu UUID oluşturur ve `X-Request-ID` yanıt başlığına yazar.

```json
{"code":"VALIDATION_ERROR","message":"Request validation failed","requestId":"UUID","fieldErrors":{"name":"Invalid value"}}
```

| Durum | HTTP | Kod |
|---|---|---|
| DTO alan doğrulaması | 422 | VALIDATION_ERROR |
| Bozuk JSON | 400 | INVALID_REQUEST |
| Bulunamayan rota | 404 | NOT_FOUND |
| Yanlış yöntem | 405 | METHOD_NOT_ALLOWED |
| Desteklenmeyen içerik türü | 415 | UNSUPPORTED_MEDIA_TYPE |
| Beklenmeyen hata | 500 | INTERNAL_ERROR |

Yanıtlarda rejected value, exception message veya stack trace bulunmaz. Test amaçlı endpointler yalnız test classpath'inde bulunur. Kimlik katmanı 401, erişim reddi 403 ve sahiplik guard'ı 404 sözleşmesini kullanır. İlaç/doz domain durumları kendi fazlarında test edilecek. Swagger UI ve OpenAPI Phase 3 kapsamında kullanılabilir.

## Phase 2 auth/account

| Yöntem | Endpoint | Girdi | Başarılı sonuç |
|---|---|---|---|
| POST | /api/v1/auth/register | email, password, timezone | 201 account |
| POST | /api/v1/auth/login | email, password | 200 tokens |
| POST | /api/v1/auth/refresh | refreshToken | 200 yeni tokens |
| POST | /api/v1/auth/logout | refreshToken | 204, gövdesiz |
| GET | /api/v1/account/me | Authorization: Bearer accessToken | 200 account |

```json
{"email":"fictional@example.invalid","password":"fictional-password-123","timezone":"Europe/Istanbul"}
```

Kayıt sonucu ve `/account/me`:

```json
{"id":"UUID","email":"fictional@example.invalid","timezone":"Europe/Istanbul"}
```

Login/refresh sonucu:

```json
{"accessToken":"REDACTED","tokenType":"Bearer","expiresIn":600,"refreshToken":"REDACTED"}
```

Refresh/logout girdisi: `{"refreshToken":"REDACTED"}` (gerçekte 43 karakter base64url). Kayıt otomatik login yapmaz. Owner/userId istemciden alınmaz. `/account/me?userId=...` yine yalnız oturum sahibini döndürür.

Doğrulama: email en fazla 254 karakter, kayıt parolası en az 12 karakter ve en fazla 72 UTF-8 byte, timezone geçerli IANA ID. 422 `VALIDATION_ERROR`; mükerrer kayıt 409 `RESOURCE_CONFLICT`; hatalı login/refresh 401 `INVALID_CREDENTIALS`; eksik/geçersiz/expired/revoked access 401 `UNAUTHORIZED`. Yanıtlarda standart requestId ve fieldErrors bulunur. Kimliksiz bilinmeyen korumalı rota da 401 döner.

Refresh rotasyonu tek kullanımlıdır. Eski token replay'i veya eşzamanlı iki refresh oturumun tamamını iptal eder; kazanan isteğin yeni token'ı da kullanılamaz. İstemci refresh çağrılarını tekleştirmelidir. Logout aynı token ile tekrar çağrıldığında 204; farklı login oturumları korunur. Auth yanıtları `Cache-Control: no-store`; limiter sınırında 429 `RATE_LIMITED` ve `Retry-After` bulunur.

## Phase 3 medications

Bütün ilaç endpointleri Bearer access token gerektirir. Owner token'dan alınır; yabancı ID ve bulunmayan ID aynı 404 NOT_FOUND döner.

| Yöntem | Endpoint | Davranış |
|---|---|---|
| POST | /api/v1/medications | 201 + Location + View |
| GET | /api/v1/medications?page=0&size=20&includeArchived=false | 200 Page, yalnız kendi kayıtları |
| GET | /api/v1/medications/{id} | 200 View, arşiv dahil |
| PATCH | /api/v1/medications/{id} | 200 View; metadata düzenleme veya arşivleme |

Create (SRS örneği):

```json
{"name":"Example Medicine A","unit":"tablet","initialStock":"30"}
```

View:

```json
{"id":"UUID","name":"Example Medicine A","unit":"tablet","initialStock":"30","archivedAt":null,"createdAt":"2026-10-22T15:00:00Z"}
```

`name`: zorunlu, whitespace olmayan en fazla 200 karakter. `unit`: zorunlu, whitespace olmayan en fazla 40 karakter. Dış boşluklar temizlenir. `initialStock`: isteğe bağlı nonnegative decimal (en fazla 13 tamsayı, 6 ondalık basamak); null/belirtilmemiş bilinmeyen, "0" bilinen sıfırdır. API çıktısı string; BigDecimal/NUMERIC ile binary floating point kullanılmaz. Bu alan başlangıç girdisidir; güncel stok ve ledger Phase 5 inventory endpointinden okunur.

PATCH örnekleri:

```json
{"name":"Example Medicine B"}
```

```json
{"archived":true}
```

Name/unit alanları bağımsız güncellenebilir; omitted/null alan değişmez. Boş patch ve archived:false 422. initialStock, id, userId ve diğer desteklenmeyen alanlar 400 INVALID_REQUEST. Alan uzunluğu/boşluk/stock hassasiyeti hataları 422 VALIDATION_ERROR. initialStock PATCH ile değiştirilemez. Arşivleme tekrarlandığında timestamp değişmez; kayıt fiziksel olarak silinmez.

Page şekli: `{"content":[View],"page":0,"size":20,"totalElements":1,"totalPages":1}`. Page 0–10000, size 1–100; sınır dışı 422. Sıralama createdAt DESC + id DESC. includeArchived=false yalnız aktif kayıtları getirir, true aktif ve arşivleri getirir. Hatalı UUID/query tipi 400; kimliksiz erişim 401; DELETE desteklenmez (kimliği doğrulanmış istek için 405).

Swagger: `/swagger-ui/index.html`; OpenAPI: `/v3/api-docs`. Önce login, sonra Authorize alanına accessToken girilir. Token kalıcı saklanmaz. Şema/arayüz erişimi auth gerektirmez; domain çağrıları gerektirir.

Medication createdAt/archivedAt değerleri UTC ISO-8601 olarak PostgreSQL mikrosaniye hassasiyetine normalize edilir; ilk yazma yanıtı ve sonraki GET aynı zamanı döndürür.

## Phase 4 — schedules / daily doses

### POST /api/v1/medications/{medicationId}/schedules

JWT gerekir; ilaç sahibine ait olmalı ve arşivli olmamalıdır. Create veya plan değişikliği aynı endpoint ile yeni sürüm oluşturur; 201 döner.

```json
{"times":["09:00","21:00"],"quantityPerDose":"1","timezone":"Europe/Istanbul","startDate":"2026-10-22","endDate":null}
```

Yanıt: id, medicationId, version (1'den artar), sıralı times, quantityPerDose (string), timezone, startDate, endDate, effectiveFrom/effectiveUntil UTC ve createdAt. Saatler benzersiz tam HH:mm, boş olmayan liste; miktar >0, en fazla 13 tamsayı/6 ondalık; IANA zone; endDate >= startDate (dahil). Eksik/uygunsuz alan 422, bozuk tarih/tip/bilinmeyen JSON alanı 400. ownerId/userId kabul edilmez. Başkasının/olmayan ilaç 404, arşivli ilaç veya gelecekte teyit edilmiş kayıtlarla çakışma 409 SCHEDULE_CONFLICT.

İlk plan geçmiş startDate alabilir; revizyon geçmiş güne uygulanamaz (422). Yeni startDate bugünün plan zone günüyse sınır request instant sonrası ilk microsecond; gelecekteyse o zone'da gün başlangıcıdır. Önceki sürümlerin aktif aralıkları kısaltılır, değişim öncesi occurrence'lar korunur. Daha ilerideki bekleyen sürümler yeni planla geçersiz kılınabilir; sürüm satırları silinmez. Yeni sürüm endDate'inden sonra eski plan kendiliğinden yeniden başlamaz.

### GET /api/v1/doses?date=2026-10-22&page=0&size=20

JWT gerekir. Date yoksa hesap timezone'unda bugün; geçmiş gün veya hesapta bugün +13 güne kadar kabul edilir. Gün filtresi hesabın timezone'undaki `[gece yarısı, ertesi gece yarısı)` UTC aralığıdır. Page 0–10000; size 1–100 (varsayılan 20), sınır dışı 422; bozuk date/parametre tipi 400.

```json
{
  "content":[{
    "id":"UUID","medicationId":"UUID","scheduleVersionId":"UUID","scheduleVersion":1,
    "medicationName":"Example Medicine A","unit":"tablet","quantityPerDose":"1",
    "dueAtUtc":"2026-10-22T06:00:00Z","localDate":"2026-10-22","localTime":"09:00",
    "timezone":"Europe/Istanbul","status":"DUE"
  }],
  "date":"2026-10-22","timezone":"Europe/Istanbul","page":0,"size":20,"totalElements":1,"totalPages":1
}
```

Sıralama dueAtUtc ASC + id ASC; yalnız owner kayıtları. `localDate/localTime/timezone` planın hedefini gösterir. DST gap'te UTC karşılığı ilerletilir, hedef saat değişmez; overlap'te ilk offset ile tek occurrence. Üretim tekrarı UUID'yi değiştirmez.

Okuma sırasında 14 günlük ufuk idempotent tamamlanır; geçmiş gün istenirse o günkü sürüme göre eksik slotlar tamamlanır. Bu kayıtlar planlanan olaylardır; gerçek alımı kanıtlamaz. PENDING/DUE zamandan türetilir; mevcut TAKEN/SKIPPED/MISSED durumları varsa korunur. Phase 5 action/stock endpointleri ve onaylı next-dose MISSED kuralı aşağıdadır.

Medication archive sonrası geçmiş saklanır, gelecekteki pending occurrences kaldırılır. Mevcut occurrence ad/birim snapshot'ları medication edit'inden etkilenmez. Swagger bu endpointleri Bearer güvenlik modeliyle gösterir.

## Phase 5 — dose actions

JWT tüm çağrılarda gerekir. POST `/api/v1/doses/{id}/actions` 201 döner. Header `Idempotency-Key: UUID` zorunludur ve body clientOperationId ile eşleşmelidir.

```json
{"action":"TAKEN","clientOperationId":"UUID","recordedAt":"2026-10-22T06:05:00Z","expectedVersion":0}
```

Action TAKEN/SKIPPED/UNDO; recordedAt zorunlu UTC timestamp ve gelecekte olamaz. expectedVersion opsiyoneldir; gönderilirse mevcut occurrence revision ile eşleşir. TAKEN/SKIPPED gelecekteki doza uygulanmaz (409). Başka sahibin/nonexistent dose 404; kimliksiz 401. Exact replay 201 + aynı semantic response; farklı içerikle aynı key 409 IDEMPOTENCY_KEY_REUSED. Header eksik/bozuk 400; header/body uyuşmazlığı 422. İzin verilmeyen state transition/stale expectedVersion 409 DOSE_STATE_CONFLICT.

```json
{"action":"UNDO","clientOperationId":"YENI-UUID","recordedAt":"2026-10-22T06:06:00Z","undoActionId":"SON-ACTION-UUID","expectedVersion":1}
```

undoActionId zorunlu ve yalnız o occurrence'ın son TAKEN/SKIPPED action'ı olmalı. Aynı undo'nun başka key ile tekrarı veya eski target 409; original key replay'i yeni bir iade yapmaz. UNDO eski kaydı silmez; TAKEN debit'ini telafi eder, SKIPPED stok değiştirmez. Sonuç zamana göre DUE/MISSED'dir.

Yanıt alanları: actionId, occurrenceId, clientOperationId, action, undoActionId, status, recordedAt (client time), serverRecordedAt, occurrenceVersion, inventoryDelta (decimal string), balance `{quantity,known,version,warnings}`. Bilinmeyen quantity null; warnings STOCK_UNKNOWN/STOCK_NEGATIVE olabilir. Replay balance ilk işlem anına aittir; güncel stok için inventory GET kullanılır.

GET `/api/v1/doses/{id}/actions?page=0&size=20`: immutable audit event history; newest occurrenceVersion önce. Page 0–10000, size 1–100. Her event ilk yanıtı ve o andaki balance'ı korur.

Daily dose View artık version, lastActionId, recordedAt ve missedAt içerir. lastActionId UNDO hedefini seçmekte kullanılabilir; son action UNDO ise yeniden undo yapılamaz. MISSED, aynı ilacın sonraki farklı planlı UTC slotu geldiğinde stok değiştirmeden oluşur. Sonraki slot yoksa DUE kalır; açık TAKEN/SKIPPED daha sonra girilebilir.

## Phase 5 — inventory

GET `/api/v1/inventory/{medicationId}?page=0&size=20`: `{medicationId,unit,balance,projection,movements}`. Movements sayfalıdır ve changeQuantity/unit/reason/doseActionId/createdAt içerir. Initial stock değil, ledger kaynaklı balance günceldir.

POST `/api/v1/inventory/{medicationId}/adjustments`, Idempotency-Key ile, 201:

```json
{"reason":"RESTOCK","quantity":"20","clientOperationId":"UUID"}
```

```json
{"reason":"CORRECTION","quantity":"17.5","clientOperationId":"YENI-UUID"}
```

RESTOCK pozitif miktar ekler; CORRECTION nonnegative mutlak gözlenen stoku ayarlar ve fark entry'si yazar. En fazla 13 tamsayı/6 ondalık; negatif, zero restock veya fazla hassas input 422. Unknown miktar RESTOCK ile known sayılmaz; CORRECTION known=true yapar. Global UUID replay/conflict kuralları dose action ile aynıdır.

Projection `{status,reason,dailyConsumption,fullDays,estimatedDepletionAt,estimatedDepletionDate,timezone}`. Status ESTIMATED veya UNKNOWN. Unknown nedenleri UNKNOWN_STOCK, NON_POSITIVE_STOCK, ARCHIVED_MEDICATION, NO_FUTURE_SCHEDULE, VARIABLE_SCHEDULE, INVALID_SCHEDULE, PLAN_ENDS_BEFORE_DEPLETION, FORECAST_HORIZON_LIMIT. Tahmin mevcut balance ve gelecek plan slotlarından, 3650 günlük sınır içinde yapılır; doz önerisi değildir.

Medication PATCH unit, opening/ledger/schedule geçmişinden sonra farklı değere değiştirilemez: 409 MEDICATION_UNIT_LOCKED. Aynı birim tekrar yazılabilir; geçmişsiz unknown-stock medication değiştirilebilir. Farklı birim için ayrı kayıt gerekir.

## FR-019 — haftalık özet

GET `/api/v1/doses/summary?weekStart=2026-10-19`: date verilmezse account zone'unda bu haftanın Pazartesisi. Yalnız Pazartesi başlayan mevcut/geçmiş hafta kabul edilir; diğerleri 422. Gün sınırları UTC'de sabit 24 saat varsaymaz.

```json
{"weekStart":"2026-10-19","weekEndExclusive":"2026-10-26","timezone":"Europe/Istanbul","scheduledDoses":12,"taken":0,"skipped":1,"missed":4,"awaitingConfirmation":0,"pending":7,"unconfirmedAtDeadline":4}
```

Current status alanları ayrışıktır ve toplamları scheduledDoses'a eşittir. unconfirmedAtDeadline tarihsel ek metriktir; sonradan TAKEN/SKIPPED olmuş bir doz için de missedAt izi kalabilir. Bu alan diğer sayaçlarla örtüşebilir, toplam içine tekrar eklenmez. Haftaya dahil etme planned due time'a göre account timezone'unda yapılır; eylemin sonradan kaydedildiği haftaya göre değildir. “Atlandı” ve “zamanında teyit girilmedi” ayrı anlamlardır; “ilaç unutuldu” sonucu çıkarılmaz.


## Phase 7 — mobil detay okumaları

- `GET /api/v1/doses/{id}`: daily View ile aynı gövde; owner scope ve medication row lock altında MISSED durumu yenilenir. Yalnız mevcut occurrence okunur; bu rota yeni slot üretmez. Yabancı/bulunamayan 404, kimliksiz 401.
- `GET /api/v1/medications/{id}/schedules?page=0&size=20`: `{content:[ScheduleView],page,size,totalElements,totalPages}`; version DESC. Parent medication owner kontrol edilir; arşivli ilacın geçmişi okunabilir. Page 0–10000, size 1–100; validation 422. Yabancı/bulunamayan parent 404.

Mobil istemci decimal miktarları string olarak taşır. Yetkili POST, access refresh sırasında gövde ve Idempotency-Key'i aynen korur. Doz/stock belirsiz retry aynı işlem kimliğidir; medication/schedule create için otomatik tekrar yoktur.


## Phase 8 — yerel hatırlatma ve erteleme

`GET /api/v1/doses/reminders?page=0&size=100`: Daily Page biçimi; date/timezone hesap gününü ve bölgesini gösterir. Normal gelecek unconfirmed slotlar 14 günlük üretim ufkuyla sınırlıdır; geçmiş dozun ileri tarihli snooze'u da dönebilir. Sıralama etkin hatırlatma zamanı (snoozedUntil varsa o, aksi halde dueAtUtc), sonra UUID. Arşivli ilaç/TAKEN/SKIPPED yoktur. Page 0–10000, size 1–100. Sahiplik principal'dan alınır.

`POST /api/v1/doses/{id}/snoozes`, `Idempotency-Key: UUID`, 201:

```json
{"clientOperationId":"UUID","snoozedUntil":"2026-10-23T18:15:00Z","expectedVersion":0}
```

Yanıt `{occurrenceId,clientOperationId,snoozedUntil,occurrenceVersion}`. snoozedUntil gelecekte olmalı; aynı anda belirli bir doz için tek güncel erteleme saklanır. Yalnız zamanı gelmiş teyitsiz doz kabul edilir; future/finalized/archived veya stale version 409 DOSE_STATE_CONFLICT. Owner dışı/bulunamayan 404; eksik auth 401; alan hatası 422. Header/body UUID eşleşir; global replay/conflict kuralları diğer mutation'larla aynıdır.

Snooze reminder metadata'sını ve occurrence revision'ını değiştirir; dueAtUtc, dose_action ve inventory_ledger yazılmaz. Zamandan türetilen MISSED kuralı bağımsızdır. TAKEN/SKIPPED/UNDO snoozedUntil'i temizler; UNDO eski ertelemeyi geri getirmez. Exact replay ilk yanıtı verir ve yeni erteleme uygulamaz. Güncel durum için dose GET kullanılır.
