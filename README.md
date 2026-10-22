# MedTrack

Türkçe ilaç planı ve stok takibi uygulaması. Ana gereksinim kaynağı [SRS](docs/SRS.md); özgün Pages belgesi [docs/source](docs/source/) altında korunur.

## Durum

Phase 0 ve Phase 1 yerel kabul kontrolleri tamamlandı. Ayrıntılı durum ve sonraki aşamalar: [proje panosu](docs/project-board.md). Mobil uygulama Phase 6'da başlayacak.

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
# .env içindeki POSTGRES_PASSWORD değerini yerel rastgele bir parolayla değiştirin.
./scripts/dev.sh db
./scripts/dev.sh test
./scripts/dev.sh run
```

İlk otomatik kurulumda yerel `.env` zaten rastgele parolayla oluşturulmuştur; onu örnek dosyayla ezmeyin. `.env` Git tarafından yok sayılır. Script bu yerel dosyayı shell ortamına yükler; yalnız güvendiğiniz yerel yapılandırmayı kullanın.

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

Backend şu anda yalnız yerel geliştirme içindir: kimlik doğrulama Phase 2'de uygulanacak. Hosted ortamlarda SRS gereği HTTPS kullanılmalı; mevcut loopback HTTP kurulumu hosted kabul kriterini karşılamaz.

## Doğrulama

```sh
./scripts/dev.sh test
cd backend
./mvnw spotless:check
# Java kaynaklarını biçimlendirmek için: ./mvnw spotless:apply
```

CI dosyası hazırdır; uzak GitHub reposu oluşturulmadığı için hosted CI çalıştırıldığı iddia edilmez. Yerel sonuçlar [phase raporunda](docs/phase-0-1-report.md).

Gerçek sağlık verisi, parola veya token repoya eklenmez. Uygulama tıbbi tavsiye ve garantili bildirim sağlamaz.
