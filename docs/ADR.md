# Architecture Decision Records

Kaynak: SRS v1.0, §6.2 ve Appendix B. 22 Ekim 2026. SRS'deki ADR numaraları korunur.

| Karar | Durum | Uygulama / zaman |
|---|---|---|
| ADR-01 Flutter/Dart | SRS kararı | Phase 6; Android önceliği handoff ile uyumlu, iOS kurulumu henüz yok |
| ADR-02 Spring Boot + JPA | SRS kararı | Java 21, Spring Boot 4.1.2; katmanlı modüler monolit |
| ADR-03 PostgreSQL + Flyway | SRS kararı | PostgreSQL 17.6; Hibernate `validate`; şemayı yalnız migration değiştirir |
| ADR-04 Cihazda bildirim | SRS kararı | Phase 8; best effort, izin farkındalığı |
| ADR-05 UUID/idempotency | Phase 5 uygulandı | Backend işlem registry/replay; offline queue Phase 9 |
| ADR-06 Değişmez geçmiş + schedule version | SRS kararı | Phase 4/5 |
| ADR-07 Yerel DB | Kütüphane açık | Drift/SQLite önerisi Phase 9 öncesi görüşülecek |
| ADR-08 MVP'de AI/ML yok | SRS kararı | Ek kapsam eklenmez |
| BUILD-01 Maven | Kullanıcı onayladı | 22 Ekim 2026; Java 21 + Maven Wrapper |
| MOBILE-01 State management | Kullanıcı onayladı / Phase 6 | 23 Ekim 2026; Riverpod, code generation olmadan |
| AUTH-01 JWT + rotated refresh | Phase 2 uygulandı | SRS §6.1/11 modeli; rotasyon, replay ve logout testleri |
| DOSE-01 UNDO | Kullanıcı onayladı / Phase 5 uygulandı | Son action için compensating entry; immutable audit |
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

## Phase 3 uygulama kararları — 22 Ekim 2026

Kaynak: FR-003, SRS §7/8/13 Phase 3 ve AC-08. Yeni ürün özelliği eklenmedi; kaynak SRS değiştirilmedi.

- V3: medications UUID, owner FK, name/unit, archived_at, created_at. Controller → service → repository; DTO dışında entity serileştirilmez.
- SRS bounded validation için name en fazla 200, unit en fazla 40 karakter; boş/whitespace reddedilir, dış boşluklar temizlenir.
- SRS create payload'ındaki `initialStock` korunur: NUMERIC(19,6)/BigDecimal, 13 tamsayı ve en fazla 6 ondalık basamak, negatif olamaz. Null/belirtilmemiş = bilinmiyor, sıfır = bilinen sıfır. API yanıtında decimal string kullanılır.
- Bu alan değişmez başlangıç girdisidir; güncel stok değildir. Phase 5 migration'ı bilinen başlangıç girdilerini tam bir kez opening ledger entry'ye dönüştürmeli. Phase 3'te inventory snapshot/ledger/tüketim veya projeksiyon uygulanmadı.
- Her okuma/güncelleme sorgusu `id AND user_id`, her liste sorgusu `user_id` içerir. Client owner/id alanları DTO'da kabul edilmez; yabancı kayıt ve olmayan kayıt aynı 404 döner.
- Liste: page 0–10000, size 1–100 (varsayılan 20), created_at DESC + id DESC. Varsayılan aktif, includeArchived=true ile arşiv dahil. Spring Page doğrudan dışarı açılmaz; kararlı Page DTO'su kullanılır.
- PATCH name/unit/archived:true alanlarını destekler. Omitted/null alanlar değişmez; boş patch reddedilir. Unarchive kapsam dışı olduğu için archived:false 422. Metadata kısmi güncellemeleri transaction içinde owner-scoped pessimistic lock kullanır.
- Arşivleme soft delete'tir; tekrar istek aynı timestamp'i korur. Kayıt ID ile sahibi tarafından okunur. Hard delete API'si yoktur. Gelecek fazlar schedule/notification arşiv etkisini ayrıca uygulamalı.
- Desteklenmeyen create/patch alanları parse aşamasında 400 INVALID_REQUEST; alan doğrulaması 422. Parametre validation da 422 ve standart hata şekli kullanır.
- Spring Boot 4 uyumlu springdoc 3.1.1. Dokümantasyon GET rotaları public; domain API'leri korumalı. OpenAPI global Bearer auth; public auth operasyonlarında security=[]; sadece `/api/v1/**` belgelenir.
- Swagger persistAuthorization=false; kurgusal örnekler ve redacted DTO string'leri, web/bind log redaction ile birlikte kullanılır.

Kaynak: [springdoc uyumluluk matrisi](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot), [Maven Central sürümleri](https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml).

Phase 3 CI düzeltmesi: PostgreSQL timestamptz mikrosaniye hassasiyetinde olduğundan medication createdAt/archivedAt kayıt öncesi MICROS'a normalize edilir. İlk create/archive yanıtı sonraki okumayla birebir eşleşir. Entegrasyon test saati alt-mikrosaniye farkını macOS'ta da üretir; nanosaniye fixture testi bu kuralı sabitler.

## Phase 4 — versioned schedules / occurrences

Kaynak: FR-004/005/012/014, BR-01/03, §7/8/10.1/13 ve AC-01/06/08'in backend plan kapsamı. Kaynak SRS değişmedi; tıbbi öneri, otomatik TAKEN veya bildirim garantisi eklenmedi.

- V4: schedule_versions + schedule_times + dose_occurrences. Metadata JPA, occurrence insert/read parametrik JDBC; aynı datasource/transaction. Backend katmanları korunur.
- Miktar pozitif BigDecimal/NUMERIC(19,6), 13 tamsayı/6 ondalık. Saatler tam HH:mm, unique ve sıralı, 1–1440 slot (bir günün dakika sayısı); geçerli IANA zone; tarih aralığı doğrulanır. Null endDate açık uçludur; endDate dahildir.
- İstenen start/end tarihleri immutable kalır; gerçek aktif aralık ayrı UTC `[effectiveFrom,effectiveUntil)` ile saklanır. PostgreSQL btree_gist exclusion constraint aynı ilaçta aralık çakışmasını engeller. V4 migration rolü btree_gist kurabilmelidir (geliştirme/CI PostgreSQL imajında hazırdır).
- İlaç başına UUID ile owner-scoped satır kilidi schedule edit, generation ve archive işlemlerini serileştirir. Versions numarası bu kilit altında artar. Günlük okuma çoklu ilaç kilitlerini UUID sırasıyla alır.
- İlk plan geçmiş startDate kabul eder. İlk oluşturma tarihinden önceki gerçek alım iddiası oluşturulmaz; türetilen plan slotları yalnız PENDING/DUE'dur. Revizyon geçmiş yerel güne uygulanamaz.
- Gelecek revizyon o günün plan zone'unda gece yarısında başlar. Bugün revizyonu mikro hassasiyetli request instant +1 microsecond ile başlar; tam değişim anındaki mevcut doz korunur ve tekrar üretilmez.
- Önceki aralıklar yeni sınırda kapanır. Daha ileride bekleyen eski sürümler boş `[from,from)` aralığa kapatılır; kimlikleri ve tanımları saklanır. Future pending occurrences silinir ve yeni sürümden üretilir. Gelecekte teyit edilmiş kayıt varsa rewrite 409 SCHEDULE_CONFLICT döner.
- İlk plan sonrası tüm PENDING geçmiş kayıtlar ve onaylı durumlar korunur. Current quantity/times/timezone eski sürümde değiştirilmez. Occurrence ilaç adı/birim snapshot'ı taşır; metadata edit'i mevcut geçmiş satırları değiştirmez.
- DST gap: ZoneRules geçiş süresi kadar ileri kaydır; overlap: earlier offset seç ve tek slot üret. 30 dakika geçişleri de aynı kurala uyar. UTC instant yanında hedef localDate/localTime ve sürümün IANA zone'u saklanır.
- Occurrence ID version UUID + local date + slot UUID'den deterministik üretilir; unique(version,date,slot) ve unique(version,UTC,slot), INSERT ON CONFLICT ile retry/parallel generation tekilleşir.
- Plan yazımı ve günlük okuma hesap zone'unda bugün +13 gün ufkunu tamamlar. Günlük okuma eski tarihse o günün eksik plan slotları da tarihsel sürümden üretilir. Background scheduler eklenmedi; bu tetikleme biçimi API sözleşmesinde açıklanır.
- GET /doses date filtrelemesi hesabın IANA zone'undaki günün UTC sınırlarıyla yapılır; 23/25 saatlik günler 24 saat varsayımı kullanmaz. Yanıttaki localDate/time schedule'ın hedef zone'undadır ve hesap gününden farklı olabilir.
- Read status: dueAt > now PENDING, aksi DUE. Otomatik TAKEN/SKIPPED yok. MISSED takip süresi SRS'de belirsizdir; Phase 5'te netleştirilecek. Veritabanı gelecekteki beş status değerini destekler.
- Archive bugüne kadarki kayıtları tutar, pending future'ı kaldırır ve aktif aralıkları kapatır. Notification cancellation mobil bildirim fazında uygulanacak.
- UTC timestamp'ler PostgreSQL micro precision ile saklanır. Eski Flyway migration'ları değiştirilmedi.

Referanslar: [Java ZonedDateTime](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/ZonedDateTime.html), [PostgreSQL range exclusions](https://www.postgresql.org/docs/17/rangetypes.html#RANGETYPES-CONSTRAINT).

## Phase 5 — onaylı dose actions / inventory / weekly summary

Kaynak: FR-006/009/010/014, BR-01/02/04, SRS §7/8/10/13 ve [onaylı ek](SRS-addendum-phase-5.md) FR-003-A/FR-019. Kullanıcı UNDO, birim koruması ve bu fazda haftalık özeti açıkça onayladı. MISSED eşiği aynı ilacın sonraki planlı dozudur.

- V5 action/operation registry, append-only inventory ledger, nullable inventory snapshot ve occurrence revision; V6 missed_at. V1–V4 değiştirilmedi. Eski bilinen initialStock bir kez OPENING'e taşınır; unknown için opening yoktur. Yeni medication aynı transaction'da snapshot/opening oluşturur.
- Global operation UUID insert + row lock, sonra owner-scoped medication lock. Canonical request fingerprint; farklı owner/target/body 409 IDEMPOTENCY_KEY_REUSED. Replay, arada stok değişse bile ilk event/balance yanıtını döndürür. İstemci eski sürümlü yanıtı güncel durum sanmamalı; gerektiğinde güncel read ile uzlaşmalı.
- Action audit, consumption/compensation, snapshot version, occurrence state/version ve kayıtlı response tek transaction'dır. Typed hata da tüm yazımları geri alır. Aynı dozda iki farklı TAKEN anahtarından yalnız biri başarılı olabilir.
- Snapshot optimistic revision SQL WHERE version kontrolüyle korunur, ayrıca JPA @Version modeli vardır. Med row lock farklı dozların aynı stoku yarışarak azaltmasını da serileştirir.
- Decimal miktarlar korunur. Stock/ledger NUMERIC(30,6); tek input NUMERIC(19,6) sınırları. Bilinen snapshot ledger toplamıdır; unknown hareketler audit edilir ama quantity null kalır. CORRECTION mutlak nonnegative gözlenen stoktur; delta = target - ledger sum ve known=true olur. RESTOCK pozitif ektir.
- TAKEN due/past teyitsiz dozda tüketim üretir; stok yetersizliği engel değildir (STOCK_NEGATIVE). SKIPPED delta 0. Future dose action 409; recordedAt gelecekteyse 422. Optional expectedVersion stale intent'i reddeder.
- UNDO yalnız observed son TAKEN/SKIPPED action ID'sini hedefler. İlk debit'in tam tersi yazılır; güncel stok miktarına veya yeni schedule quantity'ye göre tahmin yapılmaz. Eski action/ledger silinmez. Repeated undo farklı key ile 409; exact replay ek iade yapmaz. Yeni TAKEN daha sonra mümkündür.
- Action/ledger UPDATE/DELETE DB trigger'larıyla korunur; account/med parent erasure cascade'i mümkün tutulur. Account deletion API/policy Phase 10 kapsamındadır.
- MISSED derived deadline, read/action sırasında materyalize edilir. Sonraki farklı UTC slot aynı ilacın effective schedule tanımlarından hesaplanır. Slot yoksa DUE; 24 saat/ertesi takvim günü varsayılmaz. missedAt ilk deadline'ı korur, stok etkilemez. Sonradan açık teyit mümkündür.
- Haftalık FR-019 sayacı duplicate log tablosu yaratmaz; hesap zone'unda Pazartesi–ertesi Pazartesi due-time aralığını kullanır. Current status sayıları ayrışıktır; unconfirmedAtDeadline tarihsel ek metrik olarak bunlarla örtüşebilir. Geçmiş hafta gerekirse tarihsel schedule'dan tamamlanır. Background job/klinik puan/grafik/export eklenmedi.
- Sabit tek gelecek schedule için future slots simülasyonu, fullDays=floor(stock/daily consumption), UTC/DST-aware estimated depletion. Açıkça teyit edilmiş future slotlar tekrar tüketim sayılmaz. Çoklu gelecek sürüm/unknown/non-positive stock/archived/no schedule/plan bitişi/3650 gün sınırı açık UNKNOWN nedenleri döndürür.
- Stock veya plan geçmişi varsa unit değişimi 409 MEDICATION_UNIT_LOCKED; aynı değer noop. Geçmişsiz unknown-stock kaydında serbest. Legacy occurrence/stock unit farklılığında TAKEN 409 MEDICATION_UNIT_CONFLICT; otomatik dönüşüm yok.

Referans: [PostgreSQL transaction isolation](https://www.postgresql.org/docs/17/transaction-iso.html), [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).


## Phase 6 — Flutter foundations — 23 Ekim 2026

Kullanıcı Riverpod seçimini açıkça onayladı. SRS Phase 6 çıkış kapısı giriş + ilaç listesi; yeni ürün kapsamı eklenmedi. Android ilk platform; iOS kurulumu önceki handoff'taki gibi sonraya bırakıldı.

- Flutter 3.47.7/Dart 3.13.5; Riverpod 3.4.3, go_router 18.1.1, http 1.6.0, flutter_secure_storage 11.2.0. Çözümlenen sürümler pubspec.lock içinde sabitlenir; CI aynı Flutter sürümünü kullanır.
- Notifier tabanlı session; go_router redirect guard; provider ile enjekte edilen transport/store. UI auth tokenlarını görmez. Async medication provider session durumuna bağlıdır; logout/session değişiminde önceki veriler ekrandan kaldırılır.
- Tek refresh Future ve tek request retry; consumed refresh tekrar gönderilmez. Refresh'te ağ hatasının sonucu belirsiz olabileceğinden yerel session kapatılıp yeniden login istenir. Normal liste bağlantı hatası session'ı silmez.
- Access+refresh birlikte tek güvenli kayıt olarak yazılır. Kalıcı kayıt başarısızsa login tamamlanmaz ve alınan server session iptal edilmeye çalışılır. Silme başarısızsa erişim bloke edilir; retry eski tokenları yüklemek yerine silmeyi tekrarlar.
- Yerel cleartext sadece Android debug ağ güvenliği konfigürasyonundaki loopback/emulator adreslerine açıktır. Hosted/release HTTPS zorunludur. Redirect HTTP transport düzeyinde takip edilmez. Backend CORS kapsamı değiştirilmedi.
- İlk tasarım sistemi Material 3, Türkçe locale, sistem açık/koyu tema, tutarlı boşluk/input/button/error öğeleri. İlaç listesinde yalnız metadata gösterilir; başlangıç stoku güncel bakiye olarak sunulmaz.
- iOS, offline DB/cache/queue, notifications ve ek ürün ekranları bu fazda yoktur. Mobil kayıt ekranı Phase 6 kapısı için gerekli değildir; backend kayıt API'si kullanılabilir.

Kaynaklar: [Flutter routing](https://docs.flutter.dev/ui/navigation), [Riverpod ProviderScope](https://pub.dev/documentation/flutter_riverpod/latest/flutter_riverpod/ProviderScope-class.html), [secure storage Android yapılandırması](https://pub.dev/packages/flutter_secure_storage).


## Phase 7 — online Flutter akışları — 23 Ekim 2026

- Yeni ürün tercihi veya kütüphane kararı gerektirilmedi; Riverpod/go_router/http temeli korundu. UUID doğrudan mobil bağımlılık yapıldı (önceden transitifti).
- Domain typed modelleri ve online API/provider katmanı eklendi. Nested routes ilaç form/detay/plan/stok ve günlük/doz hiyerarşisini korur; doğrudan detay yolu da üst ekrana geri döner.
- İlaç ve plan iki ayrı backend transaction'ı olduğu için UI bunları ayrı kayıt adımları olarak sunar. Birinci adım başarılıysa ikinci adımın hatası yeni ilaç oluşturmaya yol açmaz.
- Doz/stok işlemleri session-scoped in-memory pending registry ile dondurulur. UUID, recordedAt, miktar ve expectedVersion retry sırasında değişmez; aynı scope'ta tek in-flight istek. Kesin conflict sonrası refresh; belirsiz transport/5xx için exact replay. Disk kuyruğu ve process-restart garantisi Phase 9'a bırakıldı.
- Medication/schedule create idempotent değildir; belirsiz sonuçta form tekrar yazmayı kapatır ve mevcut kayıt/sürüm kontrolüne yönlendirir. PATCH metadata ve archive mevcut backend sözleşmesini kullanır.
- Miktarlarda double kullanılmaz. Hesap-zone gün anahtarları backend'den gelir; istemcinin gün/hafta anahtarı aritmetiği UTC takvim tarihidir. Plan bölgeleri ve UTC action zamanları ayrı etiketlenir.
- Mobil doz detayı/plan geçmişi için iki owner-scoped okuma API'si eklendi; yeni şema/migration veya auth politikası yoktur. History pagination lazy mapping transaction içinde yapılır; collection fetch ile sayfa sınırı bellekte uygulanmaz.
- Stok rozeti mevcut sayfadaki ilaçlar için sınırlı inventory okumaları kullanır; büyük veri optimizasyonu/performance gate Phase 10 değerlendirmesine bırakılır.
