# Phase 8 — Yerel bildirimler

23 Ekim 2026. Kaynak: SRS FR-007/008/012, NFR-11, §9/10/13 ve AC-06/09. Kullanıcı gerçek Android cihaz testini şimdilik erteledi; geliştirme ve doğrulama emülatörle yapıldı. Gerçek cihaz ve üreticiye özgü pil kısıtları doğrulanmış sayılmaz.

## Uygulama

- Hatırlatma ayarlarında bağlamsal izin isteme, reddedilmiş/kapalı izin durumu, sistem ayarlarına geçiş, pil optimizasyonu açıklaması. İzin reddi ilaç/doz ekranlarını engellemez; açılışta otomatik izin isteme yoktur.
- Varsayılan bildirimde ilaç adı gizlidir. Kullanıcı isterse adı göstermeyi seçebilir; tercih değişince eski içerik kaldırılıp yeniden planlanır. Android kanal görünürlüğü private'dır. Bildirim yalnız doz detayını açar; Aldım/Atladım otomatik atanmaz.
- Sunucunun IANA/DST politikasından üretilen UTC anı platforma UTC olarak gönderilir. Plugin'in yerel saati ikinci kez çözerek DST'nin tekrarlanan saatinde ertelemeyi kaydırması önlenir. Cihaz bölgesinin değişmesi kullanım planının bölgesini otomatik değiştirmez.
- Kullanıcı/occurrence kimliğinden türeyen pozitif Android bildirim kimliği, çakışma çözümüyle güvenli yerel kayıtta korunur. Açılış/resume, plan/metadata değişikliği, arşiv ve doz işlemi sonrası bildirimler uzlaştırılır. Eski bilinen doz/ilaç bildirimleri, yenileme bağlantısı başarısız olsa da kaldırılır.
- En yakın 200 aday için tek seferlik bildirim kurulur; normal plan ufku hesap bölgesinde bugünden 14 gündür. İleri tarihli erteleme ayrı adaydır. Sınır aşılırsa kısmi planlama açıklanır. Uygulama açılmadan ufuk kendiliğinden sonsuza genişlemez.
- Saat doğruluğu izni yoksa inexactAllowWhileIdle, varsa exactAllowWhileIdle kullanılır. Hassas izin ayrıca ve yalnız kullanıcı isteğiyle yönetilir; teslim garantisi veya tam ekran/acil alarm iddiası yoktur.
- Native boot/package-replaced receiver ve küçük bildirim ikonu/desugaring ayarları eklendi. Çıkışta OS bildirimleri ve yerel hatırlatma kaydı temizlenir; eski ağ/secure-store yanıtlarının tekrar bildirim oluşturması önlenir. Temizlik hatasında token yine silinir, yeniden deneme gerekir.
- Doz detayında 5/10/15/30 dakika erteleme: yalnız hatırlatma zamanı değişir. Planlanan doz zamanı, teyit geçmişi ve stok değişmez. Doz zaten teyit edilmişse erteleme reddedilir. Gelecekteki doz için erteleme sunulmaz. Aldım/Atladım/UNDO eski erteleme bilgisini kaldırır.

## Backend ve şema

V7 `dose_occurrences.snoozed_until` ekler. `GET /api/v1/doses/reminders` future unconfirmed slotları ve gelecekteki ertelemeleri sayfalı döndürür. Arşiv/TAKEN/SKIPPED aday değildir. `POST /api/v1/doses/{id}/snoozes` aynı global işlem registry'siyle idempotenttir; owner, UUID başlığı, alan ve sürüm kontrolleri uygulanır. Doz View artık snoozedUntil taşır. SRS'nin ertelenen hatırlatma kaydı API'si tamamlanmıştır; yeni ürün özelliği değildir.

## Doğrulama

- Backend: **113 test**; Maven verify/Spotless başarılı. V4→V7 upgrade/no-op ve geliştirme V6→V7 geçişi doğrulandı.
- Mobil: **51 unit/widget testi**; format ve fatal-info analyzer kontrolleri geçti. ID çakışması/stabilitesi, UTC/DST, izin reddi/iptali, hassas izin kaybında fallback, rescheduling, offline stale cancellation, gizlilik, owner/callback filtresi, logout/fetch/persistence yarışları kapsanır.
- Android API 37 emülatörü: izin reddi, izinsiz hassas zamanlamada yaklaşık kurulum, izinli gerçek OS teslimi, gizlilik, plan değişikliği, snooze/stock invariance, arşiv/çıkış temizliği senaryoları çalıştırıldı.
- Phase 6 oturum ve Phase 7 ilaç/plan/TAKEN–UNDO–SKIPPED/stok/arşiv Android regresyon testleri geçti. Phase 7 test yardımcısı, klavye boyut değişikliği bitmeden lazy form düğmesine kaydırma yarışını önlemek için focus kapanışını bekler.
- Gerçek OS bildirim panelindeki bildirime dokunarak doğru doz detayının açıldığı ve teyit kaydı oluşmadığı doğrulandı. Erteleme sonrası 28 bekleyen kayıt ve kararlı kimlikler gözlendi.
- Normal uygulama açılışı ve arka plana alma sonrası emülatör yeniden başlatıldı: başlangıçtaki 28 kayıttan zamanı gelen tek seferlik erteleme çıktı; kalan 27 kimlik ve 27 gerçek OS alarmı korundu. Aynı APK üzerine kurulduğunda package-replaced receiver sonrası aynı 27 kimlik/alarm doğrulandı.
- Emülatör deep Doze IDLE durumuna alındığında 27 OS alarmı kayıtlı kaldı; ardından Doze zorlaması kaldırıldı. Bu kontrol, Doze altında teslim gecikmesinin ölçüldüğü anlamına gelmez.
- Flutter test kapanışının force-stop yaptığı ayrı negatif senaryoda cache korunurken OS alarmları yoktu. Salt plugin cache kaydı aktif OS alarmını kanıtlamaz. Android 15+ force-stop sırasında PendingIntent kayıtlarını iptal eder; kullanıcı uygulamayı tekrar açmalıdır ([Android davranışı](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state), [Doze test yöntemi](https://developer.android.com/training/monitoring-device-state/doze-standby)).

Test aracı: [notification_emulator_test.py](../mobile/tool/notification_emulator_test.py). Kurgusal example.invalid hesapları kullanılır; fixture'lar geliştirme DB'sinde kalır. Native test aracı yalnız emulator-* cihazlarını kabul eder; test hesabı uygulamadaki oturumun yerine geçer. Kişisel veri kullanılmaz.

## Sınırlamalar ve sonraki doğrulama

Gerçek Android cihaz/OEM pil politikaları, fiziksel yeniden başlatma ve iOS testi ertelendi. Emülatördeki gözlem bu cihazlar için garanti değildir. Force-stop, DND, sistem izinleri, cihazın kapalı olması ve güç kısıtları teslimi etkileyebilir. Yerel bildirim cache'i offline doz action kuyruğu değildir; tam offline/sync Phase 9'dadır. Gerçek kullanıcı pilotundan önce fiziksel cihaz kabul matrisi tamamlanmalıdır.
