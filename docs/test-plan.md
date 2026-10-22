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
