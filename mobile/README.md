# MedTrack mobil — Phase 6–8

Android öncelikli Türkçe online istemci. Riverpod seçimi kullanıcı tarafından onaylandı. Phase 7'de ilaç, plan, günlük/geçmiş doz, teyit/UNDO, stok ve haftalık özet akışları tamamlandı. Phase 8 Android bildirimleri eklendi; fiziksel cihaz testi ertelendi. Offline DB/sync Phase 9'da.


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

Giriş ekranındaki **Hesabın yok mu? Kayıt ol** bağlantısıyla hesap oluşturulur. E-posta, en az 12 karakterli şifre, şifre tekrarı ve saat dilimi istenir. Varsayılan `Europe/Istanbul` değiştirilebilir; IANA bölgesini backend doğrular. Şifre UTF-8 olarak en fazla 72 byte olabilir. Başarılı kayıt otomatik oturum açmaz; giriş ekranına dönerek başarı mesajı gösterir. Mükerrer hesap/alan/ağ hataları güvenli mesajlarla sunulur. Gerçek hasta verisi kullanma. İlaçlarım → İlaç ekle üzerinden ilaç oluştur; detayından kullanım planını gir. Başlangıç stoku bilinmiyorsa boş bırak. Günlük ekranından doz seçip açık teyit ver; stok ekranından hareketleri incele.

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
- İlaç listesinde `initialStock` güncel stok olarak gösterilmez; decimal veriler string olarak korunur. Güncel bakiye stok ekranından okunur.

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


## Phase 7 kullanım ve bağlantı kuralları

İlaçlarım kartları detay/düzenleme/plan/arşiv akışına; Günlük sekmesi hesap bölgesindeki bugün ve tarih seçilen geçmişe; Haftalık sekmesi onaylı özete gider. Doz detayı Aldım/Atladım/UNDO için onay ister, stok defteri ve değişmez işlem geçmişini gösterir. “Zamanında teyit yok” kesin alınmama iddiası değildir. Stok düzeltmesinde ek miktar yerine fiziksel toplam girilir. Tahminler açıkça “Tahmini”dir; uygun veri yoksa gerekçe gösterilir.

Belirsiz doz/stok isteğinde “Aynı işlemi tekrar dene” özgün kimlik ve payload'ı kullanır. Bu kayıt yalnız mevcut session belleğinde tutulur; uygulamayı kapatıp açma veya offline queue garantisi Phase 9'a aittir. Yeni ilaç/plan oluşturma yanıtı belirsizse kör tekrar yerine liste/plan geçmişi kontrol edilir. Formlar ondalık virgülü noktaya dönüştürür; floating point hesap yapmaz.

Tüm native akış testleri için backend açıkken `flutter test integration_test -d emulator-5554` çalıştır. `phase7_test.dart` kurgusal hesapla online ilaç/plan/TAKEN/UNDO/SKIPPED/stock/edit/archive akışını kontrol eder. Testler geliştirme emülatöründe kurgusal session kullanır ve çıkışla biter; kişisel hesabını kullanmaz. Fixture'lar geliştirme DB'sinde kalır.


## Phase 8 — bildirim kullanımı ve test

İlaçlarım → zil → Hatırlatma ayarları. Uygulama kendiliğinden izin istemez; açıklamayı okuduktan sonra hatırlatmaları aç. Varsayılan içerikte ilaç adı yoktur. İzin reddi ilaç/doz akışlarını engellemez. Daha hassas zamanlama isteğe bağlıdır; izin yoksa yaklaşık zamanlama kullanılır. Pil kısıtları ve force-stop dahil OS davranışları teslimi etkileyebilir; kesin alarm garantisi yoktur.

Doz detayındaki erteleme 5/10/15/30 dakika seçenekleri sunar. Plan ve stok değişmez; teyit edilen veya gelecekteki doz ertelenemez. Erteleme kaydı saklanır; cihazda aktif bildirim için bildirimlerin açık olması gerekir. Sonraki 14 günlük plan uygulama açıldığında/geri dönüldüğünde ve mutasyon sonrasında yenilenir; en yakın 200 aday için kurulum yapılır. Çıkış bütün cihaz hatırlatmalarını temizler; tekrar girişte bu hesap için yeniden açmak gerekir.

Backend açık ve Android emülatörü bağlıyken:

```sh
python3 tool/notification_emulator_test.py --mode denied
python3 tool/notification_emulator_test.py --mode inexact
python3 tool/notification_emulator_test.py --mode granted
python3 tool/notification_emulator_test.py --mode tap
```

Helper yalnız emulator-* cihazlarını kabul eder; kurgusal fixture ile test APK'sını kurup izin durumunu ayarlar. Bu testler emülatördeki oturumu kurgusal test oturumuyla değiştirir; geliştirme hesabınla yeniden giriş gerekebilir. `persistTap` modu bildirimleri reboot/upgrade gözlemi için bırakır. Bu mod sonrası normal debug APK'yı kurup uygulamayı aç; normal arka plana alma ile force-stop'u birbirine karıştırma. Normal `flutter test integration_test` bildirim senaryosu için önceden verilmiş POST_NOTIFICATIONS ve exact alarm izni varsayar; tam izin matrisi için helper kullanılır. Yerel fixture'lar geliştirme DB'sinde kalır.

Native test sonrasında normal uygulama giriş noktasını geri kur:

```sh
flutter build apk --debug
adb -s emulator-5554 install -r build/app/outputs/flutter-apk/app-debug.apk
adb -s emulator-5554 shell am start -n com.medtrack.medtrack/.MainActivity
```

Emülatör gözlemleri ve ertelenen gerçek cihaz matrisi [Phase 8 raporundadır](../docs/phase-8-report.md). iOS bu fazda derlenmedi/test edilmedi.

## Yerel web önizlemesi

Backend açıkken, `mobile` dizininde:

```sh
flutter build web --debug --dart-define=API_BASE_URL=http://127.0.0.1:7357/api/v1
python3 tool/web_preview.py
```

Tarayıcı adresi: http://127.0.0.1:7357. Yerel sunucu `/api/v1` isteklerini `127.0.0.1:8080` backend'ine iletir; backend CORS ayarı değişmez. Bu komutlar yerel debug önizlemesidir. Android cihaz bildirimleri web üzerinde kapalıdır. Tarayıcı oturumu Android oturumundan ayrıdır; kendi hesabınla giriş yapabilirsin. Sunucuyu durdurmak için Ctrl+C kullanılır.

İlaç ekleme/düzenlemede isteğe bağlı **İlaç gücü** kutudaki metni saklar (ör. `20 mg/tablet`). **Stoku neyle sayacaksın?** alanı ise stok ve kullanım miktarının birimidir (ör. `tablet`); **Şu anda elinde ne kadar var?** bu birimle miktarı alır (ör. `30`). Güç bilgisi stok hesabını değiştirmez.
