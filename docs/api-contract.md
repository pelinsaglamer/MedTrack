# API sözleşmesi — Phase 1

Kaynak: SRS §8. Domain API tabanı `/api/v1`; domain endpointleri ilgili fazlarında uygulanacak.

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

Yanıtlarda rejected value, exception message veya stack trace bulunmaz. Test amaçlı endpointler yalnız test classpath'inde bulunur. 401/403, domain 404 ve 409 davranışları kimlik ve domain fazlarında test edilecek. Swagger SRS Phase 3 kapsamıdır.
