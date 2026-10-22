# Proje panosu

Her faz kendi kapısı geçilmeden tamamlandı sayılmaz. Phase 0–2 mevcut kullanıcı kapsamıdır.

| Faz | Kapsam | Durum | Çıkış kanıtı |
|---|---|---|---|
| 0 | SRS, araçlar, repo, README, ADR, pano, CI iskeleti | Tamamlandı | Phase raporu |
| 1 | Spring Boot, PostgreSQL, migration, health, global errors | Yerel kontroller tamamlandı | 8 test geçti; build + migration + format başarılı. Hosted CI henüz çalışmadı |
| 2 | Identity/authorization | Yerel kontroller tamamlandı | 23 toplam test; JWT, refresh/replay, logout, sahiplik, log redaction |
| 3 | Medication CRUD | Bekliyor | OpenAPI ve API testleri |
| 4 | Versioned schedules | Bekliyor | DST/sınır testleri |
| 5 | Dose + inventory | Bekliyor | Retry/concurrency stok testleri |
| 6 | Flutter temel | Bekliyor | Login + liste; state kararı |
| 7 | Flutter akışları | Bekliyor | Online MVP |
| 8 | Yerel bildirimler | Bekliyor | Gerçek cihaz kanıtı |
| 9 | Offline sync | Bekliyor | Replay/conflict; DB kararı |
| 10 | Privacy/hardening | Bekliyor | Tüm MUST testleri |
| 11 | Paketleme/portfolio | Bekliyor | Demo ve teslim |

Uzak servis oluşturulmadı; bu dosya repo içinde sürümlenen proje panosudur. Kaynak SRS veya handoff metinleri değiştirilmedi.
