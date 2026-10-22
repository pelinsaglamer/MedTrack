# SRS v1.0 — Phase 5 onaylı ek

**Tarih:** 22 Ekim 2026 (Europe/Istanbul). **Durum:** Kullanıcı tarafından açıkça onaylandı.

Özgün Pages/SRS v1.0 kaynakları korunur. Bu ek aşağıdaki açık kararları netleştirir ve haftalık özet gereksinimini ekler; diğer SRS gereksinimleri geçerlidir.

## BR-01-A — MISSED teyit eşiği

Aynı ilacın etkin planındaki bir sonraki **farklı UTC doz zamanı** geldiğinde önceki teyitsiz occurrence MISSED olur. Geçerli IANA zone ve DST çözümlemesi kullanılır; sabit 24 saat varsayımı yoktur. Sonraki planlı doz yoksa DUE kalır. MISSED, ilacın alınmadığının kanıtı değildir; zamanında teyit gelmediği anlamına gelir. Otomatik stok düşümü yapılmaz. `missedAt` ilk teyit son tarihini korur; sonradan açık TAKEN/SKIPPED kaydı girilebilir.

## BR-02-A — UNDO

Yalnız o occurrence'ın son TAKEN/SKIPPED action'ı, istemcinin gönderdiği `undoActionId` ile geri alınabilir. Son action eşleşmezse 409. Eski action ve ledger entry silinmez/düzenlenmez; yeni UNDO action'ı eklenir. TAKEN için ilk tüketim hareketinin tam tersi, SKIPPED için sıfır stok etkisi vardır. Doz zamana göre yeniden DUE/MISSED olarak teyit bekler. Aynı anahtarla replay aynı yanıtı döndürür; farklı anahtarla ikinci undo veya eski action'ı undo 409'dur. UNDO'nun kendisi geri alınmaz; kullanıcı yeni bir TAKEN/SKIPPED verebilir.

## FR-003-A — Birim koruması

Başlangıç miktarı, herhangi bir stok hareketi veya plan geçmişi varsa `unit` farklı bir değere değiştirilemez: 409 MEDICATION_UNIT_LOCKED. Aynı birimi tekrar yazmak mümkündür. Hiç stok/plan geçmişi yoksa metadata düzeltmesi serbesttir. Farklı birim için ayrı ilaç kaydı açılır; otomatik miktar/doz dönüşümü yoktur. Mevcut occurrence/ledger snapshot'ları değişmez.

## FR-019 — Haftalık durum özeti (bu faz için MUST)

- Kullanıcı yalnız kendi haftalık sayaçlarını görebilir; hesap IANA timezone'u ve Pazartesi–ertesi Pazartesi gün sınırları kullanılır.
- Mevcut/geçmiş hafta; total planned, TAKEN, SKIPPED, MISSED, DUE/teyit bekleyen ve future PENDING ayrı sayılır.
- `unconfirmedAtDeadline`, sonraki doz saatine kadar sunucuya teyit ulaşmamış kayıtları ayrıca sayar; daha sonra TAKEN/SKIPPED girilmiş kayıtlar bu tarihsel bilgiyi korur. Bu sayı diğer güncel durumlarla örtüşebilir, toplamın bir alt bölümü gibi toplanmaz.
- “İlaç unuttunuz / almadınız” iddiası kullanılmaz. Önerilen etiketler: “Atlanan doz”, “Teyit bekleyen doz”, “Zamanında teyit girilmeyen doz”.
- Ayrı bir kopya log tablosu yoktur; mevcut occurrence ve append-only action/ledger geçmişinden üretilir. Grafik, klinik uyum puanı ve dışa aktarma eklenmez.
- Kabul: sayaç doğruluğu, owner izolasyonu, tarih sınırı, MISSED sonrası geç teyit/UNDO, stok değişmemesi ve Swagger sözleşmesi doğrulanır.
