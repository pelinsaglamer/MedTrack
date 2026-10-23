# Phase 7 — Flutter online kullanım akışları

23 Ekim 2026. Kaynak: SRS §9/§13 Phase 7 ve onaylı Phase 5 eki. Phase 7 kapısı online MVP'dir; bildirim ve offline sync sonraki fazlardadır. Yeni ürün özelliği eklenmedi; özgün SRS değişmedi.

## Tamamlanan mobil akışlar

- İlaç ekleme/düzenleme/arşivleme; zorunlu ad/birim, isteğe bağlı başlangıç stoku, güncel stok rozeti. Bilinmeyen stok sıfır değildir. Stok/plan geçmişi bulunan birim değiştirilemez. Arşiv için teyit istenir; geçmiş korunur.
- İlaç detayından kullanım planı oluşturma/değiştirme; saatler, doz miktarı, IANA bölgesi, başlangıç/bitiş tarihi. Plan geçmişi sayfalıdır ve yeni sürüm eski dozları değiştirmez. İlaç kaydı ve plan kaydı ayrı adımlardır; plan hatası ilacı silmez.
- Günlük görünüm, hesap bölgesindeki bugün ve tarih seçerek geçmiş kayıtlar. Plan saati/bölgesi ve durum açıklamaları gösterilir; yalnız açık kullanıcı seçimi TAKEN/SKIPPED oluşturur.
- Doz detayı: planlanan UTC/yerel zaman, bildirilen kayıt zamanı, Aldım/Atladım/UNDO teyidi ve sayfalı değişmez işlem geçmişi. MISSED “zamanında teyit yok” olarak gösterilir; kesin alınmama iddiası yoktur.
- Güncel stok, stok ekleme, mutlak fiziksel stok düzeltmesi, ledger geçmişi, negatif/bilinmeyen stok uyarıları. Projeksiyon “Tahmini” olarak ve UNKNOWN nedenleriyle gösterilir; kullanım önerisi sunulmaz.
- Onaylı haftalık sayılar mobilde gösterilir. Tarihsel teyitsizlik metriği durum sayaçlarına tekrar eklenmez.
- Yükleme/boş/hata/retry durumları, alan hataları, korumalı alt rotalar ve Android geri navigasyonu. Decimal girdiler string kalır; miktarlarda binary floating point kullanılmaz. Tarih anahtarlarında UTC takvim aritmetiği cihaz DST'sinden bağımsızdır.

## Tekrar deneme ve bağlantı davranışı

Doz ve stok yazmaları, kullanıcı onayından sonra üretilen UUID ve dondurulmuş payload ile gönderilir. İstek sürerken ikinci çağrı yapılmaz. Timeout/5xx veya belirsiz sonuçta pending işlem bu oturumun belleğinde tutulur; yeniden deneme aynı UUID/recordedAt/payload'ı kullanır. Başka bir seçim/ikinci stok hareketi oluşturulmaz. Kesin 4xx/conflict sonrası güncel veri alınarak yeniden teyit gerekir. JWT yenilemesi sırasında POST gövdesi ve idempotency başlığı korunur.

Pending kayıtlar diske yazılmaz; uygulama kapanması sonrası offline replay garantisi yoktur. Çıkışta pending işlemler ve kullanıcı provider verileri bırakılır. Disk kuyruğu/senkronizasyon Phase 9'dadır. İlaç/plan oluşturma API'lerinde idempotency sözleşmesi olmadığı için belirsiz yanıtta kör tekrar kapatılır; liste/plan geçmişini kontrol etme yolu gösterilir.

## Backend desteği

Mobil detay akışları için owner-scoped `GET /api/v1/doses/{id}` ve sayfalı `GET /api/v1/medications/{id}/schedules` eklendi. Doz okuması aynı ilaç kilidi altında türetilen durumu yeniler. Yabancı ve olmayan kayıt aynı 404 döner; GET endpointleri de JWT gerektirir. Plan geçmişi en yeni sürüm önce döner; page/size sınırları korunur. Yeni migration yok; şema V6'dır.

## Doğrulama

- Backend: 108 test; 0 hata/başarısızlık/atlanan, Maven verify ve Spotless başarılı. Yeni okuma uçlarında sahiplik, bulunamayan kayıt, kimlik ve sayfalama sınırı test edildi.
- Mobil: 32 unit/widget testi başarılı. Dart format ve `flutter analyze --fatal-infos` sorunsuz. Android debug APK derlendi.
- Android API 37 emülatörü + gerçek geliştirme backend'i: kayıt/giriş/secure-store restore; ilaç/plan oluşturma; Aldım → UNDO → Atladım; stok ekleme/düzeltme; birim koruması; düzenleme/arşiv ve geçmiş koruması uçtan uca doğrulandı. Phase 6 auth regresyonu ve Phase 7 kullanım akışı toplam 2 native entegrasyon testidir.
- Test verileri kurgusal `example.invalid` hesaplarıdır. Fixture'lar geliştirme DB'sinde kalır; gerçek hasta verisi kullanılmaz. Fiziksel cihaz testi yapıldığı iddia edilmez.

CI: [Backend](https://github.com/pelinsaglamer/MedTrack/actions/workflows/backend.yml), [Mobile](https://github.com/pelinsaglamer/MedTrack/actions/workflows/mobile.yml). Emülatör entegrasyon testleri yerelde ayrıca çalışır; CI format/analyze/unit-widget/debug APK kontrollerini çalıştırır.

Çalıştırma: [mobil README](../mobile/README.md). Sonraki adım Phase 8: izin farkındalığı olan yerel bildirimler ve cihaz doğrulaması. iOS, bildirim garantisi, offline sync ve hesap silme bu fazda tamamlandı sayılmaz.


Ardışık stok işlemlerinde miktar alanı, önceki isteğin yanıtında temizlenmek yerine teyitten hemen sonra temizlenir; onaydan önce klavye odağı kapatılır. Bu, sonraki fiziksel stok girdisinin eski işlem tarafından silinmesini önler. Native test ikinci miktarı da kontrol eder.
