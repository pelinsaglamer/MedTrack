# MedTrack mobil — Phase 6

Android öncelikli, Türkçe Flutter istemcisi. Phase 6 kapsamı: giriş, güvenli oturum, korumalı yönlendirme ve aktif ilaç listesi. Riverpod seçimi kullanıcı tarafından 23 Ekim 2026'da onaylandı. Formlar, günlük doz/stock/history ekranları Phase 7'de; bildirimler Phase 8'de, yerel DB/offline sync Phase 9'da.

## Çalıştırma

Flutter 3.47.7 / Dart 3.13.5; Android SDK ve kabul edilmiş lisanslar gerekir. Repo kökünden backend'i başlat:

```sh
./scripts/dev.sh db
./scripts/dev.sh run
```

Ayrı terminalde:

```sh
cd mobile
flutter pub get --enforce-lockfile
flutter emulators --launch Medium_Phone_API_37.0
flutter run -d emulator-5554
```

Varsayılan debug API: `http://10.0.2.2:8080/api/v1` (Android emülatöründen bilgisayara erişim). Fiziksel Android cihaz USB ile bağlıysa:

```sh
adb reverse tcp:8080 tcp:8080
flutter run -d DEVICE_ID --dart-define=API_BASE_URL=http://127.0.0.1:8080/api/v1
```

Cihaz ID'si için `flutter devices`. Hosted ortamda HTTPS adresi ver:

```sh
flutter run --dart-define=API_BASE_URL=https://YOUR_HOST/api/v1
```

URL `/api/v1` ile, sonunda `/` olmadan bitmeli; parola/token/query içeremez. HTTP yalnız debug ve `10.0.2.2`/`127.0.0.1`/`localhost` için kabul edilir. Release için HTTPS zorunlu; yayın imzası/mağaza paketi bu fazın kapsamı değildir. Backend CORS ayarı mobil native HTTP için gerekmez.

İlk hesap, yerel [Swagger](http://localhost:8080/swagger-ui/index.html) üzerinden `/auth/register` ile oluşturulur. Mobil kayıt ekranı bu fazın çıkış kapısında bulunmaz. Gerçek hasta verisi kullanma. Mevcut hesabın e-posta ve şifresiyle mobil giriş yap; ilaç yoksa boş liste gösterilir. Bu fazda ilaç eklemek için Swagger `/medications` kullanılır.

## Yapı ve güvenlik davranışı

- `lib/core`: HTTP transport, standart güvenli hata eşlemesi, secure store ve bağımlılık provider'ları.
- `lib/features/auth`: typed login/refresh/logout API, tek refresh çağrısı, session state ve giriş formu.
- `lib/features/medications`: typed modeller, aktif liste, sayfalama ve yenileme.
- `lib/shared`: Material 3 tema, renkler, boşluklar ve yükleme/hata bileşenleri. Açık/koyu sistem teması, Türkçe erişilebilir etiketler, kaydırılabilir dar ekran düzeni.
- `app.dart`: go_router; oturumsuz kullanıcı ilaç listesine gidemez. Oturum değişince eski liste provider'ları bırakılır; kullanıcı verisi diske cache edilmez.
- Access/refresh çifti yalnız `flutter_secure_storage` içinde tek JSON kaydıdır; Android platform şifrelemesi kullanılır. Android backup kapalıdır. Tokenlar/parolalar konsola yazılmaz; model `toString` çıktıları redacted.
- 401 için tek refresh + tek tekrar; paralel istekler aynı refresh Future'ını bekler. Belirsiz/başarısız rotasyonda eski refresh yeniden kullanılmaz, yerel oturum temizlenir. Bu durumda yeniden giriş gerekir.
- Normal veri isteğinde bağlantı hatası tokenları silmez. Başlangıçta bağlantı yoksa retry ekranı; refresh sonucu belirsizse yeniden giriş. Güvenli kayıt silinemezse erişim bloke edilir ve retry önce silmeyi tekrarlar.
- Çıkış sunucudaki session'ı iptal eder ve yerel tokenları temizler. Ağ hatasında yerel çıkış yapılır; sunucu iptalinin doğrulanamadığı açıkça gösterilir.
- İlaç listesinde `initialStock` güncel stok olarak gösterilmez; decimal veriler string olarak korunur. Güncel stok ekranı Phase 7'de.

## Doğrulama

```sh
dart format --output=none --set-exit-if-changed lib test integration_test
flutter analyze --fatal-infos
flutter test
flutter build apk --debug
```

Gerçek geliştirme backend'i açıkken emülatörde uçtan uca test:

```sh
flutter test integration_test/phase6_test.dart -d emulator-5554
```

Bu test yalnız kurgusal `phase6-…@example.invalid` hesabı ve örnek ilaç oluşturur; tokenları yazdırmaz. Android secure store üzerinden session restore ve logout sonrası server 401 kontrol edilir. Veri geliştirme DB'sinde kurgusal fixture olarak kalır. Test sonrası uygulama oturumu kapalıdır. Otomatik CI emülatör testini çalıştırmaz; unit/widget testleri, analyze, format ve debug APK derlemesini çalıştırır.

APK: `build/app/outputs/flutter-apk/app-debug.apk` (Git'e eklenmez). iOS, Xcode/CocoaPods ve cihaz doğrulaması kurulmadığı için bu fazda üretilmedi.
