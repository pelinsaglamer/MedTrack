# Onaylı ek — İlaç gücü ve stok alanlarının ayrılması

23 Ekim 2026. Kullanıcı, stok birimiyle ilaç gücünün karıştığını belirtti ve ayrı ilaç gücü alanını açıkça onayladı. Bu ek kaynak SRS'yi değiştirmeden onaylı kapsamı belgeler.

- İlaçta isteğe bağlı `strength` alanı bulunur; en fazla 100 karakterlik kutu bilgisi metnidir. Örnek: `20 mg/tablet`, `5 mg/5 ml`.
- Bu alan aritmetik değer, doz önerisi veya otomatik birim dönüşümü değildir. Stok ve her kullanımda alınan miktar `unit` üzerinden devam eder. Örneğin `strength=20 mg/tablet`, `unit=tablet`, elde kalan miktar `30` ayrı bilgilerdir.
- Oluşturma ve düzenlemede girilebilir, liste ve güncel ilaç detayında gösterilir. Geçmiş dozlara geriye dönük güç bilgisi eklenmez; eski kayıtlar yeniden yorumlanmaz.
- V8 nullable kolon ekler. Mevcut ilaçların gücü boş kalır; ilaç adından veya birimden çıkarım yapılmaz. Mevcut kullanıcı verileri otomatik değiştirilmez.
- API Create/Patch/View alanı `strength`'tir. Create'de eksik/null/boş metin null olur. Patch'te eksik/null mevcut değeri korur; boş veya yalnız boşluk içeren metin alanı temizler. Metnin baş/son boşlukları kaldırılır. 100 karakter üstü 422 döner. Sahiplik kuralları aynıdır.
- Stok/plan geçmişi varsa birim kilidi devam eder. Güç alanını düzenlemek stok hareketi, plan sürümü veya doz işlemi oluşturmaz.
- Form açıklamaları “ilaç gücü”, “stoku neyle sayacaksın?” ve “şu anda elinde ne kadar var?” ayrımını örneklerle açıklar. Listede “Elinde kalan” kullanılır; stok eklemek ile mevcut toplamı düzeltmek ayrı açıklanır.

Yerel web önizlemesi [mobil README](../mobile/README.md) içindedir. Web üzerindeki Android bildirim desteği kapalıdır. Bu ek, Phase 9 offline kapsamını başlatmaz.

## Doğrulama

115 backend testi geçti: ayrı güç bilgisinin create/get/list/patch/clear davranışı, eski istemci uyumu, uzunluk ve sahiplik kontrolleri, miktar/ledger değişmezliği ve eski verilerin V8'e null güç bilgisiyle geçişi doğrulandı. 52 mobil test geçti: oluşturma isteğinde güç–birim–miktar ayrımı ve stok birimi kilitliyken mevcut gücü yükleme/temizleme kapsanır. Backend paketleme/Spotless, Flutter analyzer/format ve debug web derlemesi başarılıdır.
