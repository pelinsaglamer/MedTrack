import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../shared/design.dart';
import 'data.dart';
import 'models.dart';
import 'widgets.dart';

String projectionReason(String? reason) => switch (reason) {
  'UNKNOWN_STOCK' => 'Güncel stok bilinmiyor.',
  'NON_POSITIVE_STOCK' => 'Stok sıfır veya negatif.',
  'ARCHIVED_MEDICATION' => 'İlaç arşivlenmiş.',
  'NO_FUTURE_SCHEDULE' => 'Gelecekte geçerli bir plan yok.',
  'VARIABLE_SCHEDULE' => 'Gelecekte birden çok plan sürümü var.',
  'PLAN_ENDS_BEFORE_DEPLETION' => 'Plan, stok tükenmeden bitiyor.',
  'FORECAST_HORIZON_LIMIT' => 'Tükenme hesaplama aralığının dışında.',
  'INVALID_SCHEDULE' => 'Plan bilgileri hesaplamaya uygun değil.',
  _ => 'Yeterli bilgi yok.',
};

class InventoryScreen extends ConsumerStatefulWidget {
  const InventoryScreen({super.key, required this.id});
  final String id;
  @override
  ConsumerState<InventoryScreen> createState() => _InventoryState();
}

class _InventoryState extends ConsumerState<InventoryScreen> {
  int _page = 0;
  String _reason = 'RESTOCK';
  final _quantity = TextEditingController();
  final _form = GlobalKey<FormState>();
  Object? _error;
  String? _notice;
  bool _confirming = false;
  String get scope => 'inventory/${widget.id}';
  @override
  void dispose() {
    _quantity.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    setState(() => _error = null);
    try {
      final result = await ref.read(operationsProvider.notifier).send(scope);
      if (result != null && mounted) {
        refreshOnline(ref);
        setState(() {
          _notice = 'Stok hareketi kaydedildi.';
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = e);
        refreshOnline(ref);
      }
    }
  }

  Future<void> _save(String unit) async {
    if (_confirming ||
        ref.read(operationsProvider).containsKey(scope) ||
        !_form.currentState!.validate()) {
      return;
    }
    _confirming = true;
    final quantity = normalizeDecimal(_quantity.text);
    FocusScope.of(context).unfocus();
    final accepted = await confirm(
      context,
      _reason == 'RESTOCK' ? 'Stok ekle' : 'Güncel stoku düzelt',
      _reason == 'RESTOCK'
          ? '$quantity $unit eklenecek. Onaylıyor musun?'
          : 'Güncel stok $quantity $unit olarak ayarlanacak. Bu değer fiziksel olarak gözlemlediğin toplam miktar olmalı.',
    );
    _confirming = false;
    if (!accepted || !mounted) return;
    ref.read(operationsProvider.notifier).begin(
      scope,
      '/inventory/${widget.id}/adjustments',
      {'reason': _reason, 'quantity': quantity},
    );
    _quantity.clear();
    await _send();
  }

  @override
  Widget build(BuildContext context) {
    final inventory = ref.watch(inventoryProvider((widget.id, _page)));
    final med = ref.watch(medicationProvider(widget.id));
    final pending = ref.watch(operationsProvider)[scope];
    return Scaffold(
      appBar: AppBar(
        title: const Text('Stok ve hareketler'),
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
          value: inventory,
          onRetry: () => refreshOnline(ref),
          builder: (v) => ListView(
            children: [
              Text(
                v.known
                    ? 'Güncel stok: ${v.quantity} ${v.unit}'
                    : 'Güncel stok bilinmiyor',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              if (v.warnings.contains('STOCK_NEGATIVE'))
                const Text(
                  'Stok negatif. Fiziksel miktarı kontrol edip stok düzeltmesi girebilirsin.',
                ),
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Tahmini stok süresi',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      if (v.projection.status == 'ESTIMATED')
                        Text(
                          '${v.projection.days} tam gün\nTahmini tükenme: ${displayDate(v.projection.date!)} • ${v.projection.zone}',
                        )
                      else
                        Text(
                          'Tahmin üretilemedi. ${projectionReason(v.projection.reason)}',
                        ),
                      const Text(
                        'Bu bir tahmindir; kullanım miktarı önerisi değildir.',
                      ),
                    ],
                  ),
                ),
              ),
              ErrorNotice(_error),
              if (_notice != null) Text(_notice!),
              if (pending != null) ...[
                Text(
                  '${movementLabel(pending.input['reason'] as String)}: ${pending.input['quantity']} ${v.unit}. Sonuç doğrulanmadı; aynı işlemi tekrar deneyebilirsin.',
                ),
                FilledButton(
                  onPressed: pending.busy ? null : _send,
                  child: Text(
                    pending.busy ? 'İşleniyor…' : 'Aynı işlemi tekrar dene',
                  ),
                ),
              ] else if (med.hasValue && med.value!.archivedAt == null)
                Form(
                  key: _form,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const SizedBox(height: 16),
                      DropdownButtonFormField<String>(
                        initialValue: _reason,
                        decoration: const InputDecoration(
                          labelText: 'Stok işlemi',
                        ),
                        items: const [
                          DropdownMenuItem(
                            value: 'RESTOCK',
                            child: Text('Stok ekle'),
                          ),
                          DropdownMenuItem(
                            value: 'CORRECTION',
                            child: Text('Güncel toplamı düzelt'),
                          ),
                        ],
                        onChanged: (s) => setState(() => _reason = s!),
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _quantity,
                        keyboardType: const TextInputType.numberWithOptions(
                          decimal: true,
                        ),
                        decoration: InputDecoration(
                          labelText: _reason == 'RESTOCK'
                              ? 'Eklenecek miktar (${v.unit})'
                              : 'Fiziksel toplam stok (${v.unit})',
                          errorText: fieldError(_error, 'quantity'),
                        ),
                        validator: (s) =>
                            decimalError(s, positive: _reason == 'RESTOCK'),
                      ),
                      if (!v.known)
                        const Text(
                          'Stok eklemek bilinmeyen toplamı bilinir yapmaz. Toplamı biliyorsan “Güncel toplamı düzelt” seç.',
                        ),
                      const SizedBox(height: 16),
                      FilledButton(
                        onPressed: () => _save(v.unit),
                        child: const Text('Stok işlemini kaydet'),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: 24),
              Text(
                'Stok hareketleri',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              if (v.movements.items.isEmpty)
                const Padding(
                  padding: EdgeInsets.all(20),
                  child: Text('Henüz stok hareketi yok.'),
                ),
              for (final movement in v.movements.items)
                Card(
                  child: ListTile(
                    title: Text(
                      '${movementLabel(movement.reason)} • ${movement.quantity} ${movement.unit}',
                    ),
                    subtitle: Text(displayInstant(movement.created)),
                  ),
                ),
              PageControls(
                page: _page,
                total: v.movements.totalPages,
                onChange: (p) => setState(() => _page = p),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
