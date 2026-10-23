import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../shared/design.dart';
import '../online/data.dart';
import '../online/models.dart';
import '../online/widgets.dart';

class MedicationDetailScreen extends ConsumerStatefulWidget {
  const MedicationDetailScreen({super.key, required this.id});
  final String id;
  @override
  ConsumerState<MedicationDetailScreen> createState() => _DetailState();
}

class _DetailState extends ConsumerState<MedicationDetailScreen> {
  int _page = 0;
  bool _busy = false;
  Object? _error;
  Future<void> _archive() async {
    if (_busy) return;
    setState(() => _busy = true);
    if (!await confirm(
      context,
      'İlacı arşivle',
      'Geçmiş korunur; gelecekteki bekleyen dozlar kaldırılır. Arşivlemek istiyor musun?',
    )) {
      if (mounted) setState(() => _busy = false);
      return;
    }
    if (!mounted) return;
    try {
      await ref
          .read(onlineApiProvider)
          .request(
            'PATCH',
            '/medications/${widget.id}',
            body: {'archived': true},
          );
      if (mounted) {
        refreshOnline(ref, medicationChanged: widget.id);
        context.go('/medications');
      }
    } catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final med = ref.watch(medicationProvider(widget.id));
    final plans = ref.watch(schedulesProvider((widget.id, _page)));
    final stock = ref.watch(inventoryProvider((widget.id, 0)));
    return Scaffold(
      appBar: AppBar(title: const Text('İlaç detayı')),
      body: ContentFrame(
        child: AsyncPanel(
          value: med,
          onRetry: () => refreshOnline(ref),
          builder: (m) => ListView(
            children: [
              Text(m.name, style: Theme.of(context).textTheme.headlineSmall),
              Text('Birim: ${m.unit}'),
              if (m.archivedAt != null) const Text('Arşivlenmiş ilaç'),
              const SizedBox(height: 16),
              AsyncPanel(
                value: stock,
                onRetry: () =>
                    ref.invalidate(inventoryProvider((widget.id, 0))),
                builder: (v) => Card(
                  child: ListTile(
                    title: Text(
                      v.known
                          ? 'Güncel stok: ${v.quantity} ${v.unit}'
                          : 'Güncel stok bilinmiyor',
                    ),
                    subtitle: Text(
                      v.warnings.contains('STOCK_NEGATIVE')
                          ? 'Stok negatif. Fiziksel miktarı kontrol et.'
                          : 'Stok defterinden hesaplanır.',
                    ),
                    onTap: () =>
                        context.push('/medications/${widget.id}/inventory'),
                  ),
                ),
              ),
              Wrap(
                spacing: 8,
                children: [
                  if (m.archivedAt == null)
                    OutlinedButton(
                      onPressed: () =>
                          context.push('/medications/${widget.id}/edit'),
                      child: const Text('Düzenle'),
                    ),
                  if (m.archivedAt == null)
                    FilledButton(
                      onPressed: () =>
                          context.push('/medications/${widget.id}/plan'),
                      child: const Text('Kullanım planı ekle / değiştir'),
                    ),
                  OutlinedButton(
                    onPressed: () =>
                        context.push('/medications/${widget.id}/inventory'),
                    child: const Text('Stok ve hareketler'),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              Text(
                'Plan geçmişi',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const Text(
                'Yeni plan geçmiş dozları değiştirmez. Sürümler aşağıda en yeniden eskiye gösterilir.',
              ),
              AsyncPanel(
                value: plans,
                onRetry: () =>
                    ref.invalidate(schedulesProvider((widget.id, _page))),
                builder: (p) => Column(
                  children: [
                    if (p.items.isEmpty)
                      const Padding(
                        padding: EdgeInsets.all(20),
                        child: Text('Henüz kullanım planı yok.'),
                      ),
                    for (final plan in p.items)
                      Card(
                        child: ListTile(
                          title: Text(
                            'Sürüm ${plan.version} • ${plan.quantity} ${m.unit} / doz',
                          ),
                          subtitle: Text(
                            '${plan.times.join(', ')} • ${plan.zone}\n${displayDate(plan.start)} – ${plan.end == null ? 'Bitiş yok' : displayDate(plan.end!)}\nEtkin başlangıç: ${displayInstant(plan.from)}${plan.until == null ? '' : '\nEtkin bitiş: ${displayInstant(plan.until!)}'}',
                          ),
                        ),
                      ),
                    PageControls(
                      page: _page,
                      total: p.totalPages,
                      onChange: (v) => setState(() => _page = v),
                    ),
                  ],
                ),
              ),
              ErrorNotice(_error),
              if (m.archivedAt == null)
                TextButton(
                  onPressed: _busy ? null : _archive,
                  child: Text(_busy ? 'Arşivleniyor…' : 'İlacı arşivle'),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
