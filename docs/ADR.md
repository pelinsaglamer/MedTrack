# Architecture Decision Records

Kaynak: SRS v1.0, §6.2 ve Appendix B. 22 Ekim 2026. SRS'deki ADR numaraları korunur.

| Karar | Durum | Uygulama / zaman |
|---|---|---|
| ADR-01 Flutter/Dart | SRS kararı | Phase 6; Android önceliği handoff ile uyumlu, iOS kurulumu henüz yok |
| ADR-02 Spring Boot + JPA | SRS kararı | Java 21, Spring Boot 4.1.2; katmanlı modüler monolit |
| ADR-03 PostgreSQL + Flyway | SRS kararı | PostgreSQL 17.6; Hibernate `validate`; şemayı yalnız migration değiştirir |
| ADR-04 Cihazda bildirim | SRS kararı | Phase 8; best effort, izin farkındalığı |
| ADR-05 UUID/idempotency | SRS kararı | Phase 5/9; henüz davranış uygulanmadı |
| ADR-06 Değişmez geçmiş + schedule version | SRS kararı | Phase 4/5 |
| ADR-07 Yerel DB | Kütüphane açık | Drift/SQLite önerisi Phase 9 öncesi görüşülecek |
| ADR-08 MVP'de AI/ML yok | SRS kararı | Ek kapsam eklenmez |
| BUILD-01 Maven | Kullanıcı onayladı | 22 Ekim 2026; Java 21 + Maven Wrapper |
| MOBILE-01 State management | Açık | Riverpod Phase 6 öncesi onaylanacak |
| AUTH-01 JWT + rotated refresh | Phase 2 uygulandı | SRS §6.1/11 modeli; rotasyon, replay ve logout testleri |
| DOSE-01 UNDO | SRS önerisi | Phase 5 öncesi compensating entry semantiği netleştirilecek |
| PRIVACY-01 Silme/retention | Açık | Gerçek kullanıcı pilotundan önce politika ve erasure |

## Phase 1 uygulama kararları

- `com.medtrack` altında auth, medications, schedules, doses, inventory, sync, account ve common modül paketleri; yalnız common davranışı bu fazda uygulanır.
- İlk migration yalnız SRS users temelini kurar. Domain tabloları kendi fazlarında eklenir; şema önerisindeki açık ayrıntılar erken sabitlenmez.
- Health: `/actuator/health`; DB başarısızlığını yansıtır, ayrıntıları dışarı açmaz.
- Her istek için sunucunun ürettiği UUID: `X-Request-ID`; hatada aynı requestId. Girdi başlıkları loglara yansıtılmaz.
- Hata şekli SRS §8'e uyar. Alan doğrulaması 422; bozuk JSON 400; beklenmeyen hata 500 ve ayrıntısız yanıt/log.
- Gerçek PostgreSQL entegrasyon testleri ayrı test servisiyle çalışır; H2 kullanılmaz. Test DB'si geçicidir.
- Spotless/Google Java Format build sırasında biçim kontrolü yapar.
- Initializr metadata'sının `4.1.1.RELEASE` sürüm etiketi Maven Central'da çözümlenmedi. Central metadata'sında mevcut kararlı `4.1.2` sürümü seçildi; derlemeyle doğrulanır.

Referanslar: [Initializr](https://start.spring.io/metadata/client), [Maven Central sürümleri](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml).

## Phase 2 uygulama kararları — 22 Ekim 2026

Kullanıcı Phase 2 devamını onayladı. SRS'nin JWT access + hashed/rotated refresh modeli uygulandı; yeni ürün kapsamı eklenmedi.

- BCrypt cost 12; kayıt minimum 12 karakter, maksimum 72 UTF-8 byte. Email `Locale.ROOT` ile normalize edilir. IANA zone doğrulanır.
- JWT HS256, issuer `medtrack`, audience `medtrack-mobile`, UUID subject ve session ID, 10 dakika TTL. İmzalama anahtarı ortamdan gelir; en az 32 karakter, kurulumda 48 rastgele byte'tan üretilir.
- Refresh 32 rastgele byte, base64url; SHA-256 hash'i saklanır. Parola hash'inden farklı olarak yüksek entropili rastgele token için deterministik hash lookup kullanılır.
- Login başına ayrı session ailesi, sabit 30 günlük mutlak expiry. Her yenileme eski token'ı consumed yapar. Session satırı PostgreSQL `FOR UPDATE` ile kilitlenir; refresh/replay/logout aynı kilidi kullanır.
- Replay, bütün aileyi iptal eder. 401 dönerken revoke transaction'ı commit edilir; rollback ile iptalin kaybolması önlenir.
- Access token'daki session ID ve owner her istekte DB üzerinden doğrulanır. Logout veya replay access token'ı da hemen geçersiz kılar. Ayrı login oturumları etkilenmez.
- SessionStore parametrik JDBC ile transaction içi PostgreSQL satır kilitlerini kullanır; kullanıcı modeli JPA'dır. Aynı datasource ve Spring transaction yönetimi kullanılır.
- `/account/me` controller → service → repository katmanlarından geçer. Owner yalnız doğrulanmış principal'dan alınır. `CurrentUser.requireOwnership` erişilemeyen kaydı 404 ile gizler. Sonraki domain repository'leri `resourceId AND userId` sorgusunu zorunlu kullanmalı.
- Stateless Bearer auth; cookie login/form/basic auth yok. CSRF bu nedenle kapalı; çapraz origin izinleri varsayılan olarak açılmaz.
- Auth POST'ları peer IP başına dakikada 30; limiter tek süreç içindir, bounded storage kullanır, forwarded header'a güvenmez. Dağıtık/proxy dağıtıma geçmeden tekrar ele alınacak.
- Auth yanıtları no-store. Parola/token DTO'ları `toString` içinde redacted. Web debug/SQL bind/constraint detail logları kapalı; test logları da hassas girdi için kontrol edilir.

Kaynaklar: [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
