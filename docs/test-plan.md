# Test planı ve izlenebilirlik

## Phase 0

- Repo SDK klasöründen ayrı; kaynak SRS korunmuş; Maven kararı onaylı.
- Java/Maven, Docker, Flutter/Android sürümleri gerçek komutlarla kontrol edilir.
- README + ADR + yerel pano + CI dosyası bulunur.

## Phase 1

| Gereksinim | Kanıt |
|---|---|
| §13 Phase 1 startup/health, NFR-09 | Gerçek HTTP health 200, DB indicator, ayrıntısız yanıt |
| §13 Phase 1 migrations, NFR-12 | Flyway validate, ikinci migrate 0, users tablosu |
| §7 users unique email | Farklı harf büyüklüğünde aynı email reddedilir |
| §8 hata sözleşmesi | 422/400/404/405/500 gerçek HTTP testleri |
| NFR-02/09 | 500 hassas fixture ayrıntısını yansıtmaz, requestId eşleşir |
| NFR-06/07 | Maven verify + Spotless + PostgreSQL CI işi |

Çalıştırma: `./scripts/dev.sh test`. Test DB portu 5433 ve database `medtrack_test`; geliştirme DB'sinden ayrıdır. Hata fixture endpointleri production build içinde bulunmaz.

## Sonraki fazlar

AC-01: Phase 2–4/7; AC-02–05: Phase 5/7; AC-06: Phase 4/8; AC-07: Phase 9; AC-08: Phase 2–5; AC-09: Phase 8; AC-10: Phase 10.

FR-001/002 Phase 2; FR-003 Phase 3; FR-004/005/012/014 Phase 4–5; FR-006/009/010 Phase 5; FR-007/008 Phase 8; FR-011 Phase 9; FR-013 Phase 10. Bunların tamamlandığı bu fazlarda iddia edilmez. Mobile widget, Android cihaz, notification/reboot/DST ve güvenlik kabul testleri ilgili fazların kapısıdır.

## Phase 2 — FR-001, FR-002 altyapısı; NFR-01/02/03/06/07/09

`AuthIntegrationTests`: kayıt, BCrypt hash, gizli alanların DTO dışında kalması, email normalization/unique, yanlış parola/bilinmeyen kullanıcı için aynı hata, parola byte sınırı, IANA doğrulaması, eksik/bozuk/tampered/expired/wrong issuer/wrong audience token, kendi hesabı ve başka sahibin kaydı, refresh hash/rotasyon/replay, eşzamanlı refresh, idempotent logout, oturum izolasyonu, expiry, kullanıcı silinince FK cascade ve access/refresh reddi, loglarda parola/token/email bulunmaması.

`AuthRateLimitTests`: sınır aşımında 429 + Retry-After, bir sonraki dakikada reset. Limiter güvenilir olmayan forwarded header kullanmaz.

`MedTrackApplicationTests`: Phase 1 regression'ları geçerli auth ile sürer; iki migration validate/no-op kontrol edilir. Toplam 23 test, 0 failure/error/skipped.

Sahiplik senaryosu test classpath'inde bulunan fixture endpointinde ortak guard'ı çalıştırır; gerçek ilaç endpointinin AC-08 kabulü Phase 3–5'te ayrıca doğrulanacaktır. FK cascade testi account deletion API'sinin tamamlandığı anlamına gelmez; AC-10 Phase 10'da kalır.
