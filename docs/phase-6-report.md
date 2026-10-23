# Phase 6 — Flutter foundations

23 Ekim 2026. SRS §13 Phase 6 kapsamı: routing, state management, typed API client, güvenli oturum saklama ve tasarım sistemi; çıkış kapısı giriş + ilaç listesi. Riverpod kullanıcı tarafından onaylandı. Kaynak SRS değiştirilmedi, yeni ürün özelliği eklenmedi.

## Uygulama

Android öncelikli Flutter projesi `mobile/` altında oluşturuldu. Riverpod session controller, go_router korumalı rotalar, typed auth/token/medication modelleri, HTTP istemcisi ve platform secure store uygulandı. Türkçe giriş formu, aktif ilaç listesi, sayfalama, yenileme, yükleme/boş/hata/tekrar deneme durumları hazır. Material 3 ortak tema, dar ekran ve büyük yazı desteği sağlandı.

Access/refresh tokenları tek güvenli kayıt içinde tutulur; normal disk cache yoktur. Paralel 401 istekleri tek refresh çağrısını paylaşır. Başarısız/belirsiz refresh eski tokenla tekrar denenmez. Normal ağ hatası session'ı korur; restore bağlantı hatasında retry sunar. Logout server revocation ve yerel temizliği yapar; ağ hatasında server çıkışının doğrulanamadığını bildirir. Store silme hatasında eski session'a dönmek yerine erişim bloke edilip silme tekrar edilir.

Yerel HTTP yalnız debug ve emulator/loopback adresleri içindir. Hosted/release HTTPS zorunludur; token/parola loglanmaz. Android backup kapalıdır. Liste başlangıç stokunu güncel bakiye olarak sunmaz.

## Doğrulama

- Flutter 3.47.7 / Dart 3.13.5; Android emülatörü API 37.
- 18 unit/widget testi başarılı: refresh concurrency/revocation, offline restore/logout, secure-store hataları, login validasyonu, liste, sayfalama, boş/error/retry ve dar ekran/büyük yazı.
- `flutter analyze --fatal-infos`: sorun yok. Dart format kontrolü başarılı.
- `flutter build apk --debug`: başarılı.
- Android API 37 emülatöründe gerçek backend ile 1 entegrasyon testi başarılı: login, ilaç listesi, secure-store restore, logout ve eski access token için 401.
- Kurgusal hesaplar dışında gerçek kullanıcı/hasta verisi kullanılmadı.

[Mobile CI](https://github.com/pelinsaglamer/MedTrack/actions/workflows/mobile.yml) format/analyze/test/debug APK kontrolünü her push/PR'da çalıştırır. Gerçek backend ile emülatör testi ayrıca yerel çalıştırılır; CI bu testi çalıştırmaz.

## Çalıştırma ve sınırlamalar

Komutlar, fixture davranışı ve güvenlik ayrıntıları [mobil README](../mobile/README.md) içinde. Backend `http://localhost:8080`, Android emulator API adresi `http://10.0.2.2:8080/api/v1`.

İlk teslimde hesap backend register API'siyle oluşturuluyordu; aşağıdaki kullanıcı isteğiyle mobil kayıt eklendi. İlaç formları, günlük doz, stok ve geçmiş ekranları Phase 7'de devam edecek. Bildirim Phase 8, offline DB/sync Phase 9, privacy/hardening Phase 10, yayın paketi Phase 11. iOS için tam Xcode/CocoaPods ve cihaz doğrulaması henüz yoktur; iOS derlemesi yapıldığı iddia edilmez. Android emülatörü fiziksel cihaz testi yerine geçmez.

## Şimdiye kadar tamamlananlar

| Faz | Tamamlanan kapsam |
|---|---|
| 0 | SRS/handoff incelemesi, gereksinim ve karar kayıtları, araçlar ve repo temeli |
| 1 | Java/Spring Boot, PostgreSQL, Maven Wrapper, Flyway, health/error altyapısı ve backend CI |
| 2 | Kayıt/giriş/çıkış, JWT, refresh rotasyonu/replay koruması, kullanıcı izolasyonu |
| 3 | İlaç oluşturma/listeleme/düzenleme/arşivleme ve Swagger |
| 4 | Sürümlü kullanım planları, günlük doz üretimi, UTC/IANA/DST ve geçmiş koruması |
| 5 | Aldım/Atladım/UNDO, idempotency, stok ledger/snapshot, projeksiyon, next-dose MISSED ve onaylı haftalık özet |
| 6 | Android Flutter temeli, Riverpod, güvenli session, routing, Türkçe login ve aktif ilaç listesi |

Phase 5 sonunda backend'de 106 test ve GitHub CI başarılıydı. Bu faz backend davranışını değiştirmedi. Sonraki ürün adımı Phase 7'dir; tamamlanmış bir online mobil MVP iddiası henüz yoktur.


## Kullanıcı isteği — mobil kayıt ekranı, 23 Ekim 2026

Kullanıcının “sign up kısmı oluşturup androidi tekrar aç” isteğiyle `/register` rotası ve giriş ekranından erişim eklendi. E-posta, şifre/tekrar ve değiştirilebilir IANA saat dilimi formu backend register API'sini kullanır. Şifre min 12 karakter / max 72 UTF-8 byte; kayıt sonrası giriş ekranında başarı mesajı, alan/mükerrer/ağ hatalarında güvenli hata görünümü. Register otomatik login yapmaz; mevcut backend sözleşmesi korunur.

Mobil test toplamı 20 oldu. Gerçek backend/emülatör entegrasyon testi artık kayıt ekranından hesap oluşturmayı da kapsar. Android uygulaması yeniden derlenip emülatörde açılır. Backend davranışı değişmedi.
