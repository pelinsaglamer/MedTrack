# API sözleşmesi — Phase 1–2

Kaynak: SRS §8. Domain API tabanı `/api/v1`; auth/account endpointleri aşağıdadır; ilaç ve doz endpointleri ilgili fazlarında uygulanacak.

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

Yanıtlarda rejected value, exception message veya stack trace bulunmaz. Test amaçlı endpointler yalnız test classpath'inde bulunur. Kimlik katmanı 401, erişim reddi 403 ve sahiplik guard'ı 404 sözleşmesini kullanır. İlaç/doz domain durumları kendi fazlarında test edilecek. Swagger SRS Phase 3 kapsamıdır.

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
