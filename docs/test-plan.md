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

## Phase 3 — FR-003; FR-002 / AC-08 ilaç erişimi; NFR-01/02/06/07/09

13 MedicationIntegrationTests (gerçek HTTP + PostgreSQL):

- Create/Location/get, owner ataması ve hassas decimal başlangıç miktarı.
- Bilinmeyen stock ile sıfır ayrımı; boş/uzun name/unit, negatif/fazla hassas/fazla büyük stock doğrulaması.
- Bütün ilaç operasyonlarında auth; ikinci kullanıcının read/edit/archive/list izolasyonu (AC-08 ilaç metadata kapsamı).
- Kısmi patch, değişmez initialStock/createdAt, arşiv timestamp idempotency ve soft delete.
- Owner-scoped bounded pagination; malformed/unknown ID; unsupported owner/id/stock alanlarının reddi; boş patch.
- Eşzamanlı iki kısmi update'in iki alanı da koruması.
- Public OpenAPI/Swagger assetleri, Bearer şeması, CRUD paths/201 response, public login security=[], persistAuthorization=false.
- İlaç adının oluşturma/okuma/listeme/hatalı güncellemede loglara çıkmaması.

Toplam 36 test: 23 önceki faz regresyonu + 13 yeni test. `./scripts/dev.sh test` build, entegrasyon ve Spotless kontrolünü çalıştırır. Üç migration için validate/no-op testi geçer. Tam AC-01 schedule ve mobil fazlarını bekler; ledger ve stok doğruluğu Phase 5'te test edilecek.
