# Phase 0–1 çalışma raporu

22 Ekim 2026, Europe/Istanbul.

## Yetki ve kaynaklar

Kullanıcının güncel talebi Phase 0 ve 1 uygulamasını ve gerekli kurulum/düzenlemeleri başlatmaktır. Handoff içindeki eski “scaffold öncesi onay” yönlendirmesi bu güncel talepten ayrı değerlendirildi. SRS Appendix B'deki Maven kararı ayrıca soruldu ve kullanıcı “Evet, Maven ile devam et” yanıtını verdi. Kaynak belgeler değiştirilmeden `docs/source/` içine kopyalandı. Pages metin export'u tabloları atladığından PDF export ve metin aktarımıyla tablolar dahil SRS okundu; `SRS.md` bu aktarımı içerir.

## Phase 0 — tamamlananlar

- SDK'dan ayrı `/Users/pelinsaglamer/Developer/MedTrack` reposu `main` dalıyla oluşturuldu.
- README, ADR, sürümlenen yerel proje panosu, API sözleşmesi ve test planı hazırlandı.
- Java 21.0.12.1, Maven 3.10.0, Git 2.50.1, Flutter 3.47.7/Dart 3.13.5 doğrulandı.
- Flutter doctor Android toolchain ve lisansları doğruladı. Emulator AVD mevcut; aktif Android cihazı bu oturumda bağlı değil. Mobil build iddiası yok.
- Docker servisi kapalıydı; Docker Desktop başlatıldı. Docker server 29.9.1, Compose 5.6.0 çalışır durumda.
- Xcode/CocoaPods/Chrome uyarıları Android backend fazlarını engellemediği için kurulum yapılmadı.
- Java 21 + PostgreSQL ile `verify` çalıştıran CI dosyası hazırlandı.

## Phase 1 — tamamlananlar

- Java 21/Maven Wrapper, Spring Boot 4.1.2, Web MVC, JPA, validation, Flyway/PostgreSQL, Actuator.
- Modül paketleri; yalnız ortak altyapı davranışı uygulandı. Auth/ilaç/doz/stok özellikleri sonraki fazlarda.
- Geliştirme PostgreSQL'i 127.0.0.1:5432, ayrı geçici test PostgreSQL'i 127.0.0.1:5433.
- Yerel rastgele parola `.env` içinde (0600, Git ignore); gerçek sağlık verisi kullanılmadı.
- V1 migration: UUID users, case-insensitive benzersiz email, password_hash, timezone, UTC uyumlu created_at.
- Hibernate `ddl-auto=validate`; Flyway clean kapalı; schema migration ile yönetilir.
- Health endpoint + request UUID + standart JSON hata yanıtları; hassas girdiler hata yanıtına/loga eklenmez.
- Yerel db/test/run/stop scripti; Spotless format denetimi.

## Çalıştırılan kontroller

- `./scripts/dev.sh test`: **BUILD SUCCESS**; 8 test, 0 failure, 0 error, 0 skipped.
- Testler: gerçek HTTP health, migration validate/no-op replay, email uniqueness, 422 validation, 400 malformed JSON, 404 unknown route, 405 method, 500 redaction/requestId.
- `spotless:check`: 12 Java dosyası biçim kontrolünden geçti (`verify` içinde).
- `sh -n scripts/dev.sh`: başarılı.
- `git check-ignore .env`: `.env` yok sayılıyor.
- Paketlenmiş JAR geliştirme DB'siyle açıldı; başlangıçta V1 migration başarıyla uygulandı.
- `GET /actuator/health`: HTTP 200; `status=UP`; request ID başlığı mevcut, component ayrıntıları yok.
- Paketlenmiş uygulamada `GET /__test/failure`: standart HTTP 404. JAR içinde test sınıfları yok.

## Açık durum ve sınırlar

- GitHub remote/push yok; hosted CI sonucu yok. Yerel `verify` CI ile aynı Maven kontrolünü geçti.
- Phase 2 kimlik doğrulaması henüz yok; backend yalnız loopback üzerinde yerel geliştirmeye hazır.
- Hosted HTTPS, gerçek cihaz testleri, Flutter uygulaması ve SRS AC-01–10 henüz bu fazların çıktısı değildir.
- İlk migration temel users tablosudur; diğer domain tabloları ilgili fazlarda eklenecek.
- Riverpod (Phase 6), yerel DB kütüphanesi (Phase 9), UNDO ayrıntıları (Phase 5), retention/pilot politikası açık karar olarak korunur.
- Sonraki adım: Phase 2 register/login/refresh/logout ve kullanıcı izolasyon testleri.

## Oturum sonunda

Geliştirme PostgreSQL servisi açık bırakıldı. Backend smoke testinden sonra durduruldu; başlatma: `./scripts/dev.sh run`. Test servisi testlerden sonra durduruldu; geçici verileri silindi. Docker Desktop açık. Kalıcı geliştirme volume'u korunur.
