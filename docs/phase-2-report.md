# Phase 2 — Identity & authorization

22 Ekim 2026, Europe/Istanbul. Kullanıcı devam talebiyle Phase 2 yetkilendirildi. Kaynak SRS değiştirilmedi; ek ürün özelliği eklenmedi.

## Uygulanan kapsam

- FR-001: register/login/refresh/logout. BCrypt cost 12; normalize email; IANA timezone ve parola byte sınırı doğrulaması.
- SRS §6.1/11: kısa ömürlü JWT (10 dakika), hashed refresh token ve rotasyon; her login için ayrı 30 günlük oturum ailesi.
- V2 Flyway migration: auth_sessions, refresh_tokens, user FK cascade ve indeksler. V1 değişmedi.
- Refresh, replay ve logout session satırı kilidiyle transaction içinde seri işlenir. Replay bütün aileyi iptal eder; 401 dönerken revoke commit edilir.
- Stateless Spring Security, güvenli varsayılan korumalı rotalar, typed 401/403 hataları, request ID. Access token imza/algoritma/expiry/issuer/audience/owner/session kontrollerinden geçer.
- FR-002 altyapısı: güvenilir principal'dan owner ID, yalnız kendi hesabını döndüren controller/service/repository akışı ve ortak ownership guard. Client userId değerleri sahipliği belirlemez.
- NFR-01/02/09: env JWT secret, no-store auth yanıtları, auth rate limiter, redacted DTO string'leri ve hassas alanları göstermeyen log ayarları.
- README, ADR, API sözleşmesi, test planı ve proje panosu güncellendi.

## Doğrulama

`./scripts/dev.sh test`: **BUILD SUCCESS**, 23 test, 0 failure, 0 error, 0 skipped.

- 14 HTTP/PostgreSQL auth entegrasyon testi: kayıt/hash/serialization, normalization/duplicate, login hataları, DTO validation, JWT güvenlik durumları, owner izolasyonu, refresh/hashed storage/replay/concurrency, logout/session izolasyonu, expiry, FK cascade ve log redaction.
- 1 limiter testi: limit, 429, Retry-After ve dakika sınırında reset.
- 8 Phase 1 regresyon testi: health, migration validate/no-op, email uniqueness, 422/400/404/405/500 hata sözleşmesi. Artık korumalı route testleri geçerli auth ile çalışır.
- Spotless check: 29 Java dosyası temiz.
- Paketlenmiş JAR mevcut geliştirme DB'sinde açıldı; V2 migration başarıyla uygulandı.
- GET /actuator/health: 200 / UP.
- Kimliksiz GET /api/v1/account/me: 401 UNAUTHORIZED, UUID requestId ve no-store başlığı.
- `.env` Git dışında; yerel JWT_SECRET rastgele oluşturuldu. Test anahtarı yalnız test kaynaklarında, üretim paketinde bulunmaz.
- Hosted GitHub CI henüz çalıştırılmadı; mevcut CI işi aynı Maven verify komutunu kullanır.

## Kullanım kararları ve açık sınırlar

- Register yalnız account döndürür; login ayrıca çağrılır.
- Refresh tek kullanımlıdır: yanıt kaybından sonra eski token'ı tekrar göndermek veya paralel refresh oturumu kapatır. Flutter fazında tek refresh çağrısı paylaşılmalı; iptalde yeniden giriş istenmeli.
- Logout refresh token ile o login oturumunu kapatır; diğer login oturumları korunur. Access token'daki oturum DB'den her istekte doğrulandığı için iptal hemen etkilidir.
- Limiter tek süreçte peer IP başına dakikada 30'dur. Çok instance veya proxy deployment öncesi paylaşılan limiter/güvenilir proxy politikası gerekir.
- İlaç kaydına başka kullanıcı erişimi (AC-08) gerçek domain endpointleri geldikçe Phase 3–5'te tekrar test edilecek; mevcut test ortak ownership guard'ını kullanır.
- Account delete endpointi/erasure (AC-10), refresh kayıtlarının retention/cleanup politikası ve gerçek kullanıcı pilotu hardening'i Phase 10 kapsamındadır. FK cascade testi hesap silme özelliğinin tamamlandığını göstermez.
- Sunucu halen yalnız loopback üzerinde yerel HTTP kullanır; hosted HTTPS kurulumu yapılmadı.

## Oturum sonu ve sonraki adım

Smoke test backend'i durduruldu; geliştirme PostgreSQL açık ve verisi korunuyor. Test servisi durduruldu, geçici test verileri silindi. Backend başlatma: `./scripts/dev.sh run`; test: `./scripts/dev.sh test`.

Sıradaki faz: Phase 3 Medication CRUD, validation, archive, ownership integration tests ve OpenAPI/Swagger.
