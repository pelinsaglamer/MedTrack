import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../shared/design.dart';
import '../online/data.dart';
import '../online/models.dart';
import '../online/widgets.dart';

class MedicationFormScreen extends ConsumerStatefulWidget {
  const MedicationFormScreen({super.key, this.id});
  final String? id;
  @override
  ConsumerState<MedicationFormScreen> createState() => _MedicationFormState();
}

class _MedicationFormState extends ConsumerState<MedicationFormScreen> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController(),
      _unit = TextEditingController(),
      _stock = TextEditingController();
  bool _ready = false, _busy = false, _uncertain = false;
  Object? _error;
  @override
  void dispose() {
    _name.dispose();
    _unit.dispose();
    _stock.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_busy || _uncertain || !_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final body = <String, dynamic>{
        'name': _name.text.trim(),
        'unit': _unit.text.trim(),
      };
      if (widget.id == null && _stock.text.trim().isNotEmpty) {
        body['initialStock'] = normalizeDecimal(_stock.text);
      }
      final result = await ref
          .read(onlineApiProvider)
          .request(
            widget.id == null ? 'POST' : 'PATCH',
            widget.id == null ? '/medications' : '/medications/${widget.id}',
            body: body,
          );
      if (!mounted) return;
      refreshOnline(ref, medicationChanged: result['id'] as String);
      context.go('/medications/${result['id']}');
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _uncertain =
              widget.id == null &&
              (e is! ApiFailure || e.status == null || e.status! >= 500);
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    bool locked = false;
    if (widget.id != null) {
      final med = ref.watch(medicationProvider(widget.id!));
      final inventory = ref.watch(inventoryProvider((widget.id!, 0)));
      final plans = ref.watch(schedulesProvider((widget.id!, 0)));
      if (!med.hasValue || !inventory.hasValue || !plans.hasValue) {
        return Scaffold(
          appBar: AppBar(title: const Text('İlacı düzenle')),
          body: AsyncPanel(
            value: med.isLoading || med.hasError
                ? med
                : inventory.isLoading || inventory.hasError
                ? inventory
                : plans,
            onRetry: () => refreshOnline(ref),
            builder: (_) => const SizedBox.shrink(),
          ),
        );
      }
      locked =
          med.value!.initialStock != null ||
          inventory.value!.movements.totalPages > 0 ||
          plans.value!.items.isNotEmpty;
      if (!_ready) {
        _name.text = med.value!.name;
        _unit.text = med.value!.unit;
        _ready = true;
      }
    }
    return PopScope(
      canPop: !_busy,
      child: Scaffold(
        appBar: AppBar(
          title: Text(widget.id == null ? 'İlaç ekle' : 'İlacı düzenle'),
        ),
        body: ContentFrame(
          child: Form(
            key: _form,
            child: ListView(
              children: [
                TextFormField(
                  controller: _name,
                  enabled: !_busy && !_uncertain,
                  decoration: InputDecoration(
                    labelText: 'İlaç adı',
                    errorText: fieldError(_error, 'name'),
                  ),
                  validator: (v) =>
                      v == null || v.trim().isEmpty || v.trim().length > 200
                      ? '1–200 karakterlik bir ad girin.'
                      : null,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _unit,
                  enabled: !locked && !_busy && !_uncertain,
                  decoration: InputDecoration(
                    labelText: 'Birim (tablet, ml vb.)',
                    errorText: fieldError(_error, 'unit'),
                  ),
                  validator: (v) =>
                      v == null || v.trim().isEmpty || v.trim().length > 40
                      ? '1–40 karakterlik bir birim girin.'
                      : null,
                ),
                if (locked)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 8),
                    child: Text(
                      'Stok veya plan geçmişi olduğu için birim korunur. Farklı birim için yeni ilaç ekle.',
                    ),
                  ),
                if (widget.id == null) ...[
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _stock,
                    enabled: !_busy && !_uncertain,
                    keyboardType: const TextInputType.numberWithOptions(
                      decimal: true,
                    ),
                    decoration: InputDecoration(
                      labelText: 'Başlangıç stoku (isteğe bağlı)',
                      helperText: 'Boş: bilinmiyor. 0: bilinen sıfır stok.',
                      errorText: fieldError(_error, 'initialStock'),
                    ),
                    validator: (v) => decimalError(v, optional: true),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Kaydettikten sonra kullanım saatlerini ve doz miktarını ekleyebilirsin.',
                  ),
                ],
                ErrorNotice(_error),
                if (_uncertain) ...[
                  const Text(
                    'Kayıt sunucuya ulaşmış olabilir. Yeniden oluşturmadan önce ilaç listesini kontrol et.',
                  ),
                  TextButton(
                    onPressed: () => context.go('/medications'),
                    child: const Text('Listeyi kontrol et'),
                  ),
                ],
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: _busy || _uncertain ? null : _save,
                  child: Text(_busy ? 'Kaydediliyor…' : 'Kaydet'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
