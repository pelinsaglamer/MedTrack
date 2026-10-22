# Phase 4 — Versioned schedules & daily doses

22 Ekim 2026, Europe/Istanbul. Kullanıcı Phase 4 devamını yetkilendirdi. SRS v1.0 değişmeden kaynak olarak korundu; kapsam dışı ürün geliştirmesi eklenmedi.

## Uygulananlar

- FR-004: ilaç sahibine özel günlük plan, pozitif hassas miktar, unique HH:mm saatler, IANA zone, inclusive start/end dates.
- `POST /api/v1/medications/{medicationId}/schedules`: yeni sürüm, sorted times, quantity string, UTC effective boundaries ve sürüm numarası.
- FR-005: `GET /api/v1/doses` bugün/geçmiş görünümü, account-zone gün sınırları, capped pagination, 14 günlük rolling generation.
- FR-012 / BR-03: önceki sürümlerin aktif aralığı kapanır; geçmiş occurrence/UUID/quantity/timezone ve metadata snapshot'ları korunur; future pending yeniden oluşturulur. Bekleyen future version aynı veya daha erken gelecek sınırdan yeniden değiştirilebilir.
- DST gap geçiş süresi kadar ileri; overlap earlier offset ile tek slot. Hedef yerel tarih/saat ve UTC instant birlikte korunur.
- PENDING/DUE zamanı yansıtır; otomatik TAKEN veya SKIPPED yoktur.
- V4 schedule_versions, schedule_times, dose_occurrences; PostgreSQL exclusion/unique/FK/check constraints. V1–V3 değiştirilmedi.
- Schedule edit/archive/generation aynı ilaç satır kilidini kullanır. Deterministik UUID ve conflict-safe insert retry'ı tekilleştirir.
- Archive geçmişi tutar; future pending'i siler, etkin plan aralıklarını kapatır ve yeniden üretimini önler.
- Swagger/OpenAPI, README, ADR, API sözleşmesi, test planı ve pano güncellendi.

## Doğrulama

- `./scripts/dev.sh test`: **BUILD SUCCESS**; 61 test, 0 failure, 0 error, 0 skipped.
- Önceki 37 regresyon + 18 yeni HTTP/PostgreSQL schedule testi + 6 DST/time resolver testi.
- Spotless check: 51 Java dosyası temiz.
- Flyway dört migration validate/no-op; DB overlap ve duplicate constraints doğrudan SQL ile doğrulandı.
- Geliştirme sunucusu yeni sürümle yeniden başlatıldı. V4 migration başarıyla uygulandı.
- GET /actuator/health: 200 / UP.
- GET /v3/api-docs ve /swagger-ui/index.html: 200; Phase 4 endpointleri şemada mevcut.
- `git diff --check`: başarılı; yerel secret ve build dosyaları Git dışında.
- GitHub'daki güncel CI sonucu Actions sayfasından izlenir.

## Davranış ayrıntıları / sınırlar

- İlk plan geçmiş başlangıç alabilir; geçmiş gün okuması planlanan slotları tarihsel sürümden tamamlar. Bunlar gerçek alım beyanı değildir.
- Revizyon geçmiş yerel güne uygulanamaz. Bugünkü değişiklik request instant sonrası ilk microsecond'da, gelecek tarihli değişiklik plan zone'undaki gün başlangıcında etkilidir. Tarih yılları 1–9998 aralığında doğrulanır; ertesi gün sınırı güvenli hesaplanır.
- GET date hesabın zone'undadır; response localDate/time plan zone'undadır. 23/25 saatlik günlerde 24 saat ekleme varsayımı yapılmaz.
- Üretim plan yazımı ve günlük okumada tetiklenir; background job eklenmedi. Yeni future date sorguları bugün +13 güne kadar, geçmiş sorgular tarihsel sürüm aralıklarıyla sınırlıdır.
- MISSED takip süresi SRS'de sayısal belirtilmedi; rastgele eşik eklenmedi. Mevcut stored final durumları korunur. Eşik ve doz geçişleri Phase 5'te netleştirilecek.
- Future finalized record overwrite edilmez; plan rewrite 409 döner. Bu koruma Phase 5 action/ledger transaction tasarımıyla birlikte sürdürülecek.
- Dose action API'si, inventory ledger, stock forecast ve bildirim kurma bu faza eklenmedi. Phase 5/8 kapsamındadır.
- V4 btree_gist extension kurar. Yerel/CI rolü bu yetkiye sahiptir; hosted migration rolünde yetki ayrıca sağlanmalı.
- Tam AC-01 mobil akışı ve AC-06 notification yenilemesi henüz tamamlanmaz. Bu faz backend plan/history kapsamını doğrular.

## Oturum sonu

Sunucu **açık bırakıldı**: http://127.0.0.1:8080/swagger-ui/index.html. Geliştirme PostgreSQL açık; geçici test servisi durduruldu. Var olan geliştirme verileri korunur.

Sonraki faz: Phase 5 transactional TAKEN/SKIPPED/UNDO, append-only inventory ledger, idempotency/concurrency/replay testleri ve stock projection. SRS'deki UNDO ayrıntıları ve MISSED takip süresi bu davranışlar uygulanmadan netleştirilmeli.
