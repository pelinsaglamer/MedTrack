import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../shared/design.dart';
import '../online/models.dart';
import 'notification_controller.dart';

class NotificationSettingsScreen extends ConsumerWidget {
  const NotificationSettingsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(notificationControllerProvider);
    final controller = ref.read(notificationControllerProvider.notifier);
    return Scaffold(
      appBar: AppBar(title: const Text('Hatırlatma ayarları')),
      body: ContentFrame(
        child: ListView(
          children: [
            Text(
              'Cihaz hatırlatmaları',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 12),
            const Text(
              'Bildirimler planını hatırlatmak içindir. Telefonun izinleri, pil kısıtları veya kapalı olması nedeniyle gecikebilir ya da görünmeyebilir. Kesin veya acil medikal alarm değildir.',
            ),
            const SizedBox(height: 16),
            if (!state.supported)
              const Text(
                'Bu sürümde bildirimler yalnız Android için hazırlanmıştır.',
              )
            else ...[
              SwitchListTile(
                title: const Text('İlaç hatırlatmaları'),
                subtitle: Text(state.enabled ? 'Bu hesap için açık' : 'Kapalı'),
                value: state.enabled,
                onChanged: state.busy
                    ? null
                    : (v) => v ? controller.enable() : controller.disable(),
              ),
              if (!state.allowed) ...[
                const Text(
                  'Bildirim izni kapalı veya henüz verilmedi. İzin vermesen de ilaç ve doz listelerini kullanabilirsin.',
                ),
                FilledButton(
                  onPressed: state.busy ? null : controller.enable,
                  child: const Text('Bildirim izni iste'),
                ),
              ],
              OutlinedButton(
                onPressed: controller.openSettings,
                child: const Text('Sistem bildirim ayarlarını aç'),
              ),
              SwitchListTile(
                title: const Text('İlaç adını bildirimde gizle'),
                subtitle: const Text(
                  'Varsayılan görünüm: “İlaç hatırlatıcınız var”.',
                ),
                value: state.redacted,
                onChanged: state.busy ? null : controller.setRedacted,
              ),
              const SizedBox(height: 16),
              Text(
                state.exact
                    ? 'Saat doğruluğu izni açık. İşletim sistemi yine de teslimi etkileyebilir.'
                    : 'Yaklaşık zamanlama kullanılır; hatırlatmalar gecikebilir.',
              ),
              if (!state.exact && state.allowed)
                OutlinedButton(
                  onPressed: state.busy ? null : controller.requestExact,
                  child: const Text('Daha hassas zamanlama iznini yönet'),
                ),
              if (state.batteryRestricted) ...[
                const SizedBox(height: 16),
                const Text(
                  'Pil optimizasyonu etkin. Telefonun arka plan kısıtları bildirimleri etkileyebilir.',
                ),
                TextButton(
                  onPressed: controller.openBatterySettings,
                  child: const Text('Pil ayarlarını aç'),
                ),
              ],
              const SizedBox(height: 16),
              Text('Cihazda bekleyen hatırlatma: ${state.count}'),
              if (state.until != null)
                Text(
                  'Plan aralığının son günü: ${displayDate(state.until!.subtract(const Duration(days: 1)).toIso8601String().substring(0, 10))}',
                ),
              if (state.limited)
                const Text(
                  'Cihaz sınırları için en yakın 200 hatırlatma kuruldu. Uygulamayı düzenli açarak planı yenile.',
                ),
              const Text(
                'Hatırlatmalar uygulama açıldığında ve plan değiştiğinde yenilenir. Uzun süre uygulama açılmazsa ileri tarihli hatırlatmalar yenilenemez.',
              ),
              if (state.error != null)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Semantics(
                    liveRegion: true,
                    child: Text(
                      state.error!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ),
                ),
              const SizedBox(height: 16),
              OutlinedButton(
                onPressed: state.busy ? null : controller.sync,
                child: Text(
                  state.busy ? 'Güncelleniyor…' : 'Hatırlatmaları yenile',
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
