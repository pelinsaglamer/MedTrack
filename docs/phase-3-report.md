# Phase 3 — Medication CRUD

22 Ekim 2026, Europe/Istanbul. Kullanıcı Phase 3'ü SRS'ye uygun sürdürmeyi yetkilendirdi. Kapsam dışı özellik eklenmedi; kaynak SRS değiştirilmedi.

## Kapsam ve değişiklikler

- FR-003: create/read/list/update/archive API, controller/service/repository/DTO katmanları.
- FR-002 / AC-08: ilaç sorgularında owner token'dan alınır; yabancı okuma/düzenleme/arşivleme/listeme engellenir.
- V3 medications migration; owner FK, metadata, arşiv tarihi, created_at ve hassas başlangıç stock girdisi.
- initialStock SRS payload'ına uygun korunur; güncel stok/ledger değildir. Phase 5'te opening ledger geçişi yapılacak.
- DTO/parametre doğrulaması; name/unit bounds; capped sayfalama; boş/geçersiz değerler için standart 422.
- PATCH transaction ve owner-scoped satır kilidi; tekrar archive aynı timestamp'i korur. Kayıt silinmez.
- springdoc 3.1.1 ile OpenAPI ve Swagger UI; Bearer Authorize, public auth şeması, token persistence kapalı.
- README, ADR, API sözleşmesi, test planı ve faz panosu güncellendi.

## Doğrulama

- `./scripts/dev.sh test`: **BUILD SUCCESS**, 37 test, 0 failure/error/skipped.
- Yeni 14 ilaç entegrasyon testi; önceki 23 auth/foundation testi regresyon olarak geçti.
- Spotless check: 36 Java dosyası temiz.
- Gerçek PostgreSQL üzerinde üç Flyway migration validate edildi; yeniden migrate 0 değişiklik.
- Paketlenmiş JAR geliştirme DB'sinde başladı; V3 migration başarıyla uygulandı.
- GET /actuator/health: 200 UP.
- GET /v3/api-docs: 200, medication paths mevcut.
- GET /swagger-ui/index.html: 200.
- `git diff --check`: başarılı. Yerel `.env` ve derleme çıktıları ignore kurallarıyla dışarıda.

## Açık sınırlar / sonraki adım

- initialStock değişmez başlangıç girdisidir; inventory ledger/current quantity/forecast Phase 5'tedir. Phase 5 migration'ı bilinen başlangıçları yalnız bir kez ledger'a taşımalıdır.
- Schedule oluşturma, arşivlenen ilacın gelecekteki occurrence/notification etkisi ve geçmişin korunması Phase 4/8'de ele alınacak.
- Unarchive ve hard-delete bu faza eklenmedi. Account deletion/retention Phase 10 kapsamındadır.
- Tam AC-01 henüz tamamlanmaz; schedule ve mobile fazları beklenir. AC-08'in ilaç metadata read/write/list erişimi doğrulandı; schedule/dose/inventory izolasyonu kendi fazlarında sürecek.
- Sunucu yalnız yerel loopback HTTP; hosted HTTPS ve gerçek cihaz doğrulaması henüz yok.
- Sonraki aşama: Phase 4 versioned schedules, zaman doğrulaması, occurrence generator, timezone/DST testleri.

Smoke test backend'i ve geçici test PostgreSQL servisi durduruldu; geliştirme PostgreSQL açık, verisi korunur. Başlatma: `./scripts/dev.sh run`. Swagger: http://127.0.0.1:8080/swagger-ui/index.html.

İlk GitHub CI çalışması Linux nanosaniye/PostgreSQL mikrosaniye precision farkını yakaladı: create/archive yanıtı sonraki GET ile eşleşmiyordu. Entity timestamp'leri kayıt öncesi mikrosaniyeye normalize edildi. Test clock'u macOS'ta da nanosaniye üretir; ayrıca deterministik hassasiyet testi eklendi. Sonuç güncel Actions çalışmasından izlenir.
