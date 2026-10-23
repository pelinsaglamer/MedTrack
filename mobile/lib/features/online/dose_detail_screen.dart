import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../shared/design.dart';
import 'data.dart';
import 'models.dart';
import 'widgets.dart';
import '../notifications/notification_controller.dart';

class DoseDetailScreen extends ConsumerStatefulWidget {
  const DoseDetailScreen({super.key, required this.id});
  final String id;
  @override
  ConsumerState<DoseDetailScreen> createState() => _DoseDetailState();
}

class _DoseDetailState extends ConsumerState<DoseDetailScreen> {
  bool _confirming = false;
  Object? _error;
  int _page = 0;
  String? _notice;
  String get scope => 'dose/${widget.id}';
  Future<void> _send() async {
    setState(() => _error = null);
    try {
      final snooze =
          ref
              .read(operationsProvider)[scope]
              ?.input
              .containsKey('snoozedUntil') ??
          false;
      final result = await ref.read(operationsProvider.notifier).send(scope);
      if (!mounted || result == null) return;
      refreshOnline(ref, doseChanged: widget.id);
      if (snooze) {
        final notifications = ref.read(notificationControllerProvider);
        setState(
          () => _notice = notifications.enabled && notifications.allowed
              ? 'Hatırlatma ertelendi. Plan, doz teyidi ve stok değişmedi.'
              : 'Hatırlatma zamanı kaydedildi; cihaz bildirimleri kapalı. Hatırlatma ayarlarını kontrol et. Plan ve stok değişmedi.',
        );
        return;
      }
      final warnings = ((result['balance'] as Json)['warnings'] as List)
          .cast<String>();
      setState(
        () => _notice = warnings.contains('STOCK_NEGATIVE')
            ? 'Kaydedildi. Stok negatif; fiziksel miktarı kontrol et.'
            : warnings.contains('STOCK_UNKNOWN')
            ? 'Kaydedildi. Güncel stok bilinmiyor.'
            : 'İşlem kaydedildi.',
      );
    } catch (e) {
      if (mounted) {
        setState(() => _error = e);
        refreshOnline(ref, doseChanged: widget.id);
      }
    }
  }

  Future<void> _act(Dose dose, String action) async {
    if (_confirming || ref.read(operationsProvider).containsKey(scope)) return;
    _confirming = true;
    final title = action == 'TAKEN'
        ? 'Aldım olarak kaydet'
        : action == 'SKIPPED'
        ? 'Atladım olarak kaydet'
        : 'Son seçimi geri al';
    final message = action == 'UNDO'
        ? 'Son Aldım/Atladım seçimi geri alınır; geçmiş korunur ve gerekiyorsa stok telafi edilir.'
        : action == 'TAKEN'
        ? 'Bu dozu aldığını onaylıyor musun? Stoktan ${dose.quantity} ${dose.unit} düşülecek.'
        : 'Bu dozu atladığını onaylıyor musun? Stok değişmez. Kullanım konusunda reçetendeki yönergeleri izle.';
    final accepted = await confirm(context, title, message);
    _confirming = false;
    if (!accepted || !mounted) return;
    ref.read(operationsProvider.notifier).begin(
      scope,
      '/doses/${dose.id}/actions',
      {
        'action': action,
        'recordedAt': DateTime.now().toUtc().toIso8601String(),
        'expectedVersion': dose.version,
        if (action == 'UNDO') 'undoActionId': dose.lastActionId,
      },
    );
    await _send();
  }

  Future<void> _snooze(Dose dose) async {
    if (_confirming || ref.read(operationsProvider).containsKey(scope)) return;
    _confirming = true;
    final minutes = await showDialog<int>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Hatırlatmayı ertele'),
        children: [
          const Padding(
            padding: EdgeInsets.all(16),
            child: Text(
              'Yalnız sonraki hatırlatma değişir. Bu işlem ilacı aldığın veya atladığın anlamına gelmez.',
            ),
          ),
          for (final minutes in [5, 10, 15, 30])
            SimpleDialogOption(
              onPressed: () => Navigator.pop(context, minutes),
              child: Text('$minutes dakika'),
            ),
          SimpleDialogOption(
            onPressed: () => Navigator.pop(context),
            child: const Text('Vazgeç'),
          ),
        ],
      ),
    );
    _confirming = false;
    if (minutes == null || !mounted) return;
    ref.read(operationsProvider.notifier).begin(
      scope,
      '/doses/${dose.id}/snoozes',
      {
        'snoozedUntil': DateTime.now()
            .toUtc()
            .add(Duration(minutes: minutes))
            .toIso8601String(),
        'expectedVersion': dose.version,
      },
    );
    await _send();
  }

  @override
  Widget build(BuildContext context) {
    final dose = ref.watch(doseProvider(widget.id));
    final history = ref.watch(actionsProvider((widget.id, _page)));
    final pending = ref.watch(operationsProvider)[scope];
    return Scaffold(
      appBar: AppBar(
        title: const Text('Doz detayı'),
        actions: [
          IconButton(
            tooltip: 'Yenile',
            onPressed: () => refreshOnline(ref),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: ContentFrame(
        child: AsyncPanel(
          value: dose,
          onRetry: () => refreshOnline(ref),
          builder: (d) => ListView(
            children: [
              Text(d.name, style: Theme.of(context).textTheme.headlineSmall),
              Text(
                '${d.quantity} ${d.unit} • ${displayDate(d.date)} ${d.time} • ${d.zone}',
              ),
              Text('Planlanan an: ${displayInstant(d.due.toIso8601String())}'),
              const SizedBox(height: 12),
              Text(
                statusLabel(d.status),
                style: Theme.of(context).textTheme.titleLarge,
              ),
              if (d.snoozedUntil != null)
                Text(
                  'Ertelenen hatırlatma: ${displayInstant(d.snoozedUntil!)}',
                ),
              if (d.recordedAt != null)
                Text(
                  'Son bildirdiğin kayıt zamanı: ${displayInstant(d.recordedAt!)}',
                ),
              if (d.missedAt != null)
                const Text(
                  'Bu doz için sonraki planlı doz zamanına kadar teyit girilmemişti. Bu bilgi ilacın alınmadığı anlamına gelmez.',
                ),
              if (d.status == 'PENDING')
                const Text('Gelecekteki doz için henüz teyit verilemez.'),
              if (_notice != null)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Semantics(liveRegion: true, child: Text(_notice!)),
                ),
              ErrorNotice(_error),
              if (pending != null) ...[
                Text(
                  '${pending.input.containsKey('snoozedUntil') ? 'Hatırlatmayı ertele' : statusLabel(pending.input['action'] as String)} işleminin sonucu henüz doğrulanmadı. Tekrar deneme aynı işlemi kontrol eder; ikinci stok hareketi oluşturmaz.',
                ),
                FilledButton(
                  onPressed: pending.busy ? null : _send,
                  child: Text(
                    pending.busy ? 'İşleniyor…' : 'Aynı işlemi tekrar dene',
                  ),
                ),
              ] else
                Wrap(
                  spacing: 8,
                  children: [
                    if (d.canConfirm)
                      FilledButton(
                        onPressed: () => _act(d, 'TAKEN'),
                        child: const Text('Aldım'),
                      ),
                    if (d.canConfirm)
                      OutlinedButton(
                        onPressed: () => _act(d, 'SKIPPED'),
                        child: const Text('Atladım'),
                      ),
                    if (d.canConfirm)
                      OutlinedButton(
                        onPressed: () => _snooze(d),
                        child: const Text('Hatırlatmayı ertele'),
                      ),
                    if (d.canUndo)
                      OutlinedButton(
                        onPressed: () => _act(d, 'UNDO'),
                        child: const Text('Son seçimi geri al'),
                      ),
                  ],
                ),
              TextButton(
                onPressed: () =>
                    context.push('/medications/${d.medicationId}/inventory'),
                child: const Text('İlacın stok ve hareketleri'),
              ),
              const SizedBox(height: 20),
              Text(
                'İşlem geçmişi',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              AsyncPanel(
                value: history,
                onRetry: () =>
                    ref.invalidate(actionsProvider((widget.id, _page))),
                builder: (p) => Column(
                  children: [
                    if (p.items.isEmpty)
                      const Padding(
                        padding: EdgeInsets.all(20),
                        child: Text('Henüz açık bir teyit işlemi yok.'),
                      ),
                    for (final action in p.items)
                      Card(
                        child: ListTile(
                          title: Text(statusLabel(action.action)),
                          subtitle: Text(
                            'Bildirilen zaman: ${displayInstant(action.recorded)}\nSunucu kayıt zamanı: ${displayInstant(action.serverRecorded)}\nStok hareketi: ${action.delta} ${d.unit}',
                          ),
                        ),
                      ),
                    PageControls(
                      page: _page,
                      total: p.totalPages,
                      onChange: (p) => setState(() => _page = p),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
