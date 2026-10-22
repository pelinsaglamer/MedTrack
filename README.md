# MedTrack

Türkçe ilaç planı ve stok takibi uygulaması. Ana gereksinim kaynağı [SRS](docs/SRS.md); özgün Pages belgesi [docs/source](docs/source/) altında korunur.

## Durum

Phase 0, Phase 1 ve Phase 2 yerel kabul kontrolleri tamamlandı. Ayrıntılı durum ve sonraki aşamalar: [proje panosu](docs/project-board.md). Mobil uygulama Phase 6'da başlayacak.

## Gereksinimler

- Java 21 (backend), Docker Desktop ve Docker Compose.
- Maven Wrapper repoda bulunur; global Maven gerekli değildir.
- Flutter/Android araçları sonraki mobil aşamalarda kullanılacak.

## Yerel çalıştırma

Komutları bu repo kökünde çalıştırın. Docker Desktop açık olmalı.

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
# İlk kurulumda .env yoksa:
cp .env.example .env
# .env içindeki POSTGRES_PASSWORD ve JWT_SECRET değerlerini yerel rastgele değerlerle değiştirin.
./scripts/dev.sh db
./scripts/dev.sh test
./scripts/dev.sh run
```

İlk otomatik kurulumda yerel `.env` zaten rastgele parola ve JWT imzalama anahtarıyla oluşturulmuştur; onu örnek dosyayla ezmeyin. `.env` Git tarafından yok sayılır. Script bu yerel dosyayı shell ortamına yükler; yalnız güvendiğiniz yerel yapılandırmayı kullanın.

```sh
curl http://127.0.0.1:8080/actuator/health
# Beklenen: status UP (health group listesi de dönebilir)
./scripts/dev.sh stop
```

`db` kalıcı geliştirme veritabanını başlatır. `test` ayrı 5433 portunda, ayrı ve geçici PostgreSQL veritabanıyla `verify` çalıştırır. Testler geliştirme verisini kullanmaz. Test servisi durdurulduğunda geçici veriler silinir. Geliştirme verisi named volume içinde korunur. Volume silen komutlar rutin durdurmada kullanılmaz.

## Yapı

- `backend/`: Java 21, Spring Boot 4.1.2, Maven, JPA, Flyway, Actuator.
- `infrastructure/`: yalnız loopback üzerinde PostgreSQL geliştirme/test servisleri.
- `docs/`: SRS, ADR, API sözleşmesi, test planı, faz panosu ve çalışma raporu.
- `mobile/`: Phase 6 için yer tutucu.
- `.github/workflows/backend.yml`: Java 21 + PostgreSQL üzerinde build/test/format kontrolü.

Backend şu anda yerel geliştirme içindir. Phase 2 kayıt/giriş, refresh rotasyonu, çıkış ve hesap erişimi uygulanmıştır. Hosted ortamlarda SRS gereği HTTPS kullanılmalı; mevcut loopback HTTP kurulumu hosted kabul kriterini karşılamaz.

## Doğrulama

```sh
./scripts/dev.sh test
cd backend
./mvnw spotless:check
# Java kaynaklarını biçimlendirmek için: ./mvnw spotless:apply
```

CI dosyası hazırdır; uzak GitHub reposu oluşturulmadığı için hosted CI çalıştırıldığı iddia edilmez. Yerel sonuçlar [Phase 0–1 raporunda](docs/phase-0-1-report.md) ve [Phase 2 raporunda](docs/phase-2-report.md).

Gerçek sağlık verisi, parola veya token repoya eklenmez. Uygulama tıbbi tavsiye ve garantili bildirim sağlamaz.

## Kimlik doğrulama — Phase 2

API sözleşmesi ve örnek JSON: [API belgesi](docs/api-contract.md).

- `POST /api/v1/auth/register`: email, password (en az 12 karakter, en fazla 72 UTF-8 byte), geçerli IANA timezone.
- `POST /api/v1/auth/login`: JWT access token (10 dakika) + opaque refresh token.
- `POST /api/v1/auth/refresh`: refresh token tek kullanımlık olarak döner; eski token tekrar kullanılırsa oturum ailesi iptal edilir.
- `POST /api/v1/auth/logout`: refresh token ile o oturumu iptal eder; idempotent 204.
- `GET /api/v1/account/me`: Bearer token ile yalnız token sahibinin hesabı.

`JWT_SECRET` en az 32 rastgele karakter olmalı; örnek üretim komutu `openssl rand -hex 32`. Yerel anahtar Git dışında tutulur. Test anahtarı yalnız test kaynaklarında bulunur.

CSRF kapalıdır çünkü kimlik doğrulama cookie üzerinden yapılmaz; API Bearer token kullanır. CORS için çapraz origin izni açılmamıştır. Auth istekleri tek süreçte doğrudan istemci IP'si başına dakikada 30 ile sınırlıdır. Çok instance/proxy deployment öncesi ortak limiter ve güvenilir proxy politikası gerekir.

Refresh token'ın yanıtı kaybolursa eski token'ı tekrar göndermek oturumu kapatır; istemci Phase 6'da refresh çağrılarını tekleştirmeli ve bu durumda yeniden giriş istemelidir. Her korumalı istekte DB oturum durumu doğrulanır; logout/replay iptali access token için de hemen geçerlidir.
