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

14 MedicationIntegrationTests (gerçek HTTP + PostgreSQL):

- Create/Location/get, owner ataması ve hassas decimal başlangıç miktarı.
- Bilinmeyen stock ile sıfır ayrımı; boş/uzun name/unit, negatif/fazla hassas/fazla büyük stock doğrulaması.
- Bütün ilaç operasyonlarında auth; ikinci kullanıcının read/edit/archive/list izolasyonu (AC-08 ilaç metadata kapsamı).
- Kısmi patch, değişmez initialStock/createdAt, arşiv timestamp idempotency ve soft delete.
- Owner-scoped bounded pagination; malformed/unknown ID; unsupported owner/id/stock alanlarının reddi; boş patch.
- Eşzamanlı iki kısmi update'in iki alanı da koruması.
- Nanosaniye üreten test clock'u ve deterministik precision fixture'ıyla create/archive timestamp'lerinin PostgreSQL round-trip boyunca sabit kalması.
- Public OpenAPI/Swagger assetleri, Bearer şeması, CRUD paths/201 response, public login security=[], persistAuthorization=false.
- İlaç adının oluşturma/okuma/listeme/hatalı güncellemede loglara çıkmaması.

Toplam 37 test: 23 önceki faz regresyonu + 14 yeni test. `./scripts/dev.sh test` build, entegrasyon ve Spotless kontrolünü çalıştırır. Üç migration için validate/no-op testi geçer. Tam AC-01 schedule ve mobil fazlarını bekler; ledger ve stok doğruluğu Phase 5'te test edilecek.

## Phase 4 — FR-004/005/012/014 altyapısı, BR-01/03, AC-01/06/08 backend plan kapsamı

18 ScheduleIntegrationTests + 6 ScheduleTimeResolverTests:

- 09:00/21:00 iki occurrence, UTC karşılığı, miktar, PENDING/DUE ve 14 günlük ufuk.
- Inclusive başlangıç/bitiş; yarın revizyonunda dün/bugün snapshot ve UUID'lerinin korunması.
- Aynı gün revizyonu, tam doz anında revizyon, bekleyen gelecekteki sürümün yeniden değiştirilmesi.
- Duplicate/bad HH:mm, boş liste, pozitif miktar/precision, IANA zone, tarih aralığı, owner spoof, geriye tarihli revizyon ve archived medication doğrulaması.
- Auth ve başka kullanıcının schedule write/dose read izolasyonu; account-day ve schedule local-date ayrımı.
- Archive future cleanup/no regeneration; eski occurrence ad/birim snapshot'larının korunması; sensitive name log redaction.
- Rolling horizon ilerlemesi ve eski dozların otomatik TAKEN/MISSED olmaması.
- Paged dose list, tarih ufku ve Swagger yolları.
- Eşzamanlı revizyon/read tekilleştirmesi; gelecekte teyit edilmiş fixture kaydının overwrite edilmemesi.
- PostgreSQL overlap SQLSTATE 23P01 ve duplicate occurrence SQLSTATE 23505 kontrolleri.
- Istanbul normal zaman, Berlin spring gap/autumn overlap, New York geçişleri, Lord Howe 30 dakika gap ve 23 saatlik günlük fark.

Toplam 61 test: önceki 37 regresyon + 24 yeni kontrol. Dört migration validate/no-op; 51 Java dosyası Spotless kontrolünde temiz. `./scripts/dev.sh test` ile çalıştırılır. Test clock'u nanosaniyeli sabit instant kullanır; timezone/DST senaryoları işletim sistemi saatine bağlı değildir.

TAKEN/SKIPPED/UNDO/idempotent stock action API'leri Phase 5 kapsamındadır. SQL ile verilen TAKEN fixture'ı yalnız geçmiş/gelecek koruma testidir, üretimde otomatik TAKEN davranışı değildir. MISSED için takip eşiği onayı beklenir; bildirim yenilemesi Phase 8 kabulüdür.

## Phase 5 — FR-006/009/010/014; FR-003-A/FR-019 onaylı ek; BR-01/02/04; AC-02–05/08 backend

Toplam 106 test, 0 failure/error/skipped. Önceki 61 test + 45 yeni kontrol. 66 Java dosyası format denetiminde temiz.

- 14 inventory testi: opening known/zero/unknown, decimal exactness, append-only restock/correction, unknown→known, exact replay after later changes, key reuse, owner/history isolation, validation, auth, concurrent replay/distinct restocks, history pagination, rollback ve parent erasure.
- 27 dose action/summary testi: TAKEN tek consumption, SKIPPED delta0, exact replay, conflicting/different keys, header/body, optional version, future dose/time, unknown/negative stock, owner auth/audit, parallel same/different key, immutable history after schedule revision, transaction completion rollback, forecast fixed/variable/finite/fractional/DST.
- UNDO: consumption compensation, SKIP undo delta0, same-key replay, stale target/newer action, cannot undo undo, parallel refund once, correction sonrası yalnız orijinal debit'i geri ekleme ve self-referencing audit account cascade.
- Summary: hafta/account-zone aralığı, SKIPPED/MISSED/pending ayrımı, deadline sonrası late confirmation/undo trace, owner izolasyonu, invalid week/auth.
- Birim: known/opening, restock ve schedule history kilidi; geçmişsiz unknown-stock metadata düzeltmesi serbest.
- 1 izole migration testi: yeni geçici test DB'sinde V4'e kadar migrate, old known/unknown medications oluştur, V5/V6 upgrade, one opening/no invented unknown, validate/no-op replay. Test rolü CREATE DATABASE yetkisi ister; DB rastgele medtrack_upgrade_* adıyla yaratılıp yalnız o DB silinir.
- Phase 4'e eklenen 3 deadline testi: sonraki aynı ilaç dozu anı, finite last dose DUE kalması, DST 25 saatlik deadline. Eski rolling history beklentisi kullanıcı onaylı MISSED kuralına güncellendi.

Rollback testi response tamamlanırken DB hatası üretir; action, ledger, snapshot, occurrence ve operation kayıtlarının hepsi geri alınır. Test trigger/function'ları finally içinde kaldırılır. Gerçek med verisi yoktur.

Mobil offline queue, real-device notifications ve account deletion API testi sonraki fazlardır; server replay/parent cascade testleri bunların tamamlandığı anlamına gelmez.


## Phase 6 — Flutter

`mobile/`: 18 unit/widget testi; auth single-flight refresh, revoke/timeout, secure persistence ve clear hatası, offline restore/logout; login form validasyonu; liste/boş/hata/retry/sayfalama; 320px ekran ve 2x yazı. `flutter analyze --fatal-infos` ve Dart format zorunlu. Yeni Mobile CI aynı Flutter sürümüyle bu kontrolleri ve debug APK derlemesini çalıştırır.

`integration_test/phase6_test.dart`: açık geliştirme backend'i + Android emülatörüyle kurgusal hesap/ilaç, login, Android secure store'dan fresh ProviderScope restore, liste, logout ve eski access token için server 401. CI bu testi otomatik çalıştırmaz; yerel sonuç Phase 6 raporunda tutulur. Gerçek hasta verisi yoktur; oluşturulan kurgusal fixture geliştirme DB'sinde kalır.


## Phase 7 — online MVP

Backend total 108: dose detail owner/404/401 ve türetilen state, schedule history owner/pagination. Mobil unit/widget: exact payload/key replay after network failure, double tap/in-flight, definitive conflict, 5xx pending retention, logout scope clear, JWT refresh preserving POST, decimal validation, UTC date navigation, create ambiguity guard, unknown inventory/forecast, explicit dose confirmation/cancel and stock correction confirmation.

`flutter test integration_test -d emulator-5554`: Phase 6 auth regression ve Phase 7 gerçek backend online akışı. Kurgusal kullanıcıyla UI üzerinden ilaç/plan, TAKEN/UNDO/SKIPPED, balance 10 → 8.5 → 10 → 10, restock 15 ve correction 17.5, unit lock, name edit, archive ve retained history kontrolü. CI native emulator testini otomatik çalıştırmaz. Son kanıt [Phase 7 raporunda](phase-7-report.md).
