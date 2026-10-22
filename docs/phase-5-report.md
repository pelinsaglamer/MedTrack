# Phase 5 — Doz teyidi, stok ve haftalık özet

22 Ekim 2026. Backend Phase 5 tamamlandı. Gereksinimler özgün SRS ile birlikte [kullanıcının onayladığı ek](SRS-addendum-phase-5.md) üzerinden uygulandı. Özgün kaynak belge değiştirilmedi.

## Uygulanan davranış

- TAKEN ve SKIPPED işlemleri kullanıcıya ait doz için kaydedilir. UUID işlem anahtarı ile aynı istek yeniden geldiğinde ilk yanıt döner; ikinci stok hareketi oluşmaz. Aynı anahtarın başka içerikle kullanılması reddedilir.
- UNDO yalnız o dozun son geçerli TAKEN/SKIPPED işlemini geri alır. Geçmiş silinmez; TAKEN için özgün tüketimi dengeleyen stok hareketi eklenir. SKIPPED geri alma stoku değiştirmez. Doz yeniden DUE veya MISSED olur.
- Başlangıç stoku, stok ekleme, mutlak miktar düzeltmesi, tüketim ve telafi hareketleri değiştirilemeyen ledger üzerinden izlenir. Snapshot aynı işlem içinde güncellenir. Bilinmeyen stok sıfır sayılmaz; negatif stok görünür uyarıyla korunur.
- Stok veya kullanım planı geçmişi varsa ilaç birimi değiştirilemez. Farklı birim için yeni ilaç kaydı açılır.
- Teyit edilmemiş doz, aynı ilacın sonraki planlı doz zamanında MISSED olur. Sonraki doz bulunmuyorsa DUE kalır. Bu durum ilacın kesinlikle alınmadığı anlamına gelmez. Geç teyit geçmişteki teyitsizlik zamanını silmez.
- Haftalık özet planlanan, alınan, atlanan, MISSED, teyit bekleyen ve gelecekteki dozları gösterir. `unconfirmedAtDeadline` ayrıca geçmişte süreyi aşmış dozları sayar; durum toplamına eklenmez.
- Stok projeksiyonu uygun plan için saat dilimi/DST ve ondalık miktarları kullanır. Belirsiz stok, birden çok gelecek plan sürümü veya hesaplama ufku gibi durumlarda açık neden koduyla UNKNOWN döner.

API yolları ve istek örnekleri [API sözleşmesindedir](api-contract.md). Şema geçişleri V5 ve V6'dır. Önceki verilerden bilinen başlangıç stokları tek açılış hareketiyle taşınır; bilinmeyenler bilinmeyen kalır.

## Doğrulama

`./scripts/dev.sh test`: **106 test, 0 hata, 0 başarısızlık, 0 atlanan; BUILD SUCCESS**. Java biçim kontrolü geçti. Başarılı testler işlem tekrarları/eşzamanlılık, UNDO telafisi, eski işlem çakışması, kullanıcı izolasyonu, işlem geri alma, geçmişin değiştirilememesi, hesap silme, haftalık özet, DST/ondalık projeksiyon ve V4 verisinin V5/V6'ya taşınmasını kapsar. Ayrıntı: [test planı](test-plan.md).

Geliştirme veritabanına V5/V6 başarıyla uygulandı; sunucu yeniden açıldığında şema V6 olarak doğrulandı. Health, OpenAPI ve Swagger HTTP 200 verdi. Sunucu `http://localhost:8080` üzerinde açık bırakıldı; [Swagger](http://localhost:8080/swagger-ui/index.html) kullanılabilir. Doz ve stok uçları kimlik doğrulaması gerektirir.

GitHub doğrulaması her push için [Backend CI](https://github.com/pelinsaglamer/MedTrack/actions/workflows/backend.yml) üzerinden ayrıca çalışır.

## Sıradaki kapsam

Bu rapor backend Phase 5 içindir. Mobil arayüz, çevrimdışı senkronizasyon ve bildirimlerin sonraki fazları henüz tamamlanmış değildir. Sonraki adım SRS sırasıyla Phase 6'dır. Ürün tıbbi öneri veya kesin bildirim garantisi vermez.
