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
      _strength = TextEditingController(),
      _unit = TextEditingController(),
      _stock = TextEditingController();
  bool _ready = false, _busy = false, _uncertain = false;
  Object? _error;
  @override
  void dispose() {
    _name.dispose();
    _strength.dispose();
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
        'strength': _strength.text.trim(),
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
        _strength.text = med.value!.strength ?? '';
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
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  TextFormField(
                    controller: _name,
                    enabled: !_busy && !_uncertain,
                    decoration: InputDecoration(
                      labelText: 'İlaç adı',
                      hintText: 'Kutuda yazan ilaç adı',
                      errorText: fieldError(_error, 'name'),
                    ),
                    validator: (v) =>
                        v == null || v.trim().isEmpty || v.trim().length > 200
                        ? '1–200 karakterlik bir ad girin.'
                        : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _strength,
                    enabled: !_busy && !_uncertain,
                    maxLength: 100,
                    decoration: InputDecoration(
                      labelText: 'İlaç gücü (isteğe bağlı)',
                      hintText: 'Örn. 20 mg/tablet veya 5 mg/5 ml',
                      helperText: 'Kutuda yazdığı gibi gir. Bu bilgi stok miktarını veya kullanım planını değiştirmez.',
                      helperMaxLines: 3,
                      errorText: fieldError(_error, 'strength'),
                    ),
                    validator: (v) => (v?.length ?? 0) > 100
                        ? 'En fazla 100 karakter girin.'
                        : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _unit,
                    onChanged: (_) => setState(() {}),
                    enabled: !locked && !_busy && !_uncertain,
                    decoration: InputDecoration(
                      labelText: 'Stoku neyle sayacaksın?',
                      hintText: 'Örn. tablet, kapsül veya ml',
                      helperText: 'Tablet sayısını takip edeceksen “tablet” yaz. “20 mg/tablet” bilgisi ilaç gücüdür; tablet sayısı değildir.',
                      helperMaxLines: 4,
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
                        labelText:
                            'Şu anda elinde ne kadar var? (isteğe bağlı)',
                        suffixText: _unit.text.trim().isEmpty
                            ? null
                            : _unit.text.trim(),
                        hintText: 'Örn. 30',
                        helperText: '30 tabletin varsa yukarıya “tablet”, buraya “30” yaz. Miktarı bilmiyorsan boş bırak; hiç kalmadıysa 0 yaz.',
                        helperMaxLines: 4,
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
      ),
    );
  }
}
