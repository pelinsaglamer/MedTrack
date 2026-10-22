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
| AUTH-01 JWT + rotated refresh | SRS önerisi | Phase 2 tasarım/güvenlik incelemesi |
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
