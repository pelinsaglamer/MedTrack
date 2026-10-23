import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../shared/design.dart';
import '../online/data.dart';
import '../online/models.dart';
import '../online/widgets.dart';

class ScheduleFormScreen extends ConsumerStatefulWidget {
  const ScheduleFormScreen({super.key, required this.id});
  final String id;
  @override
  ConsumerState<ScheduleFormScreen> createState() => _PlanState();
}

class _PlanState extends ConsumerState<ScheduleFormScreen> {
  final _form = GlobalKey<FormState>();
  final _times = TextEditingController(),
      _quantity = TextEditingController(),
      _zone = TextEditingController(),
      _start = TextEditingController(),
      _end = TextEditingController();
  bool _ready = false, _busy = false, _uncertain = false;
  Object? _error;
  @override
  void dispose() {
    for (final c in [_times, _quantity, _zone, _start, _end]) {
      c.dispose();
    }
    super.dispose();
  }

  String? _dateError(String? v, {bool optional = false}) {
    if (optional && (v ?? '').trim().isEmpty) return null;
    final text = (v ?? '').trim();
    final parsed = DateTime.tryParse(text);
    return !RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(text) ||
            parsed == null ||
            parsed.toIso8601String().substring(0, 10) != text
        ? 'YYYY-MM-DD biçiminde geçerli bir tarih girin.'
        : null;
  }

  Future<void> _date(TextEditingController c) async {
    final d = await showDatePicker(
      context: context,
      initialDate: DateTime.tryParse(c.text) ?? DateTime.now(),
      firstDate: DateTime(1),
      lastDate: DateTime(9998, 12, 31),
    );
    if (d != null) c.text = d.toIso8601String().substring(0, 10);
  }

  Future<void> _save() async {
    if (_busy || _uncertain || !_form.currentState!.validate()) return;
    setState(() => _busy = true);
    if (!await confirm(
      context,
      'Kullanım planını kaydet',
      'Girdiğin saatler ve miktar gelecekteki planı belirleyecek. Geçmiş kayıtlar korunur. Bilgileri onaylıyor musun?',
    )) {
      if (mounted) setState(() => _busy = false);
      return;
    }
    if (!mounted) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref
          .read(onlineApiProvider)
          .request(
            'POST',
            '/medications/${widget.id}/schedules',
            body: {
              'times': _times.text.split(',').map((e) => e.trim()).toList(),
              'quantityPerDose': normalizeDecimal(_quantity.text),
              'timezone': _zone.text.trim(),
              'startDate': _start.text.trim(),
              'endDate': _end.text.trim().isEmpty ? null : _end.text.trim(),
            },
          );
      if (mounted) {
        refreshOnline(ref, medicationChanged: widget.id);
        context.go('/medications/${widget.id}');
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _uncertain = e is! ApiFailure || e.status == null || e.status! >= 500;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final plans = ref.watch(schedulesProvider((widget.id, 0)));
    final account = ref.watch(accountProvider);
    final today = ref.watch(dailyProvider(('', 0)));
    if (!_ready) {
      if (!plans.hasValue || !account.hasValue || !today.hasValue) {
        return Scaffold(
          appBar: AppBar(title: const Text('Kullanım planı')),
          body: AsyncPanel(
            value: plans.isLoading || plans.hasError
                ? plans
                : account.isLoading || account.hasError
                ? account
                : today,
            onRetry: () {
              ref.invalidate(accountProvider);
              refreshOnline(ref, medicationChanged: widget.id);
            },
            builder: (_) => const SizedBox.shrink(),
          ),
        );
      }
      final previous = plans.value!.items.firstOrNull;
      _zone.text = previous?.zone ?? account.value!['timezone'] as String;
      _times.text = previous?.times.join(', ') ?? '';
      _quantity.text = previous?.quantity ?? '';
      _start.text = today.value!.date;
      _ready = true;
    }
    return PopScope(
      canPop: !_busy,
      child: Scaffold(
        appBar: AppBar(title: const Text('Kullanım planı')),
        body: ContentFrame(
          child: Form(
            key: _form,
            child: ListView(
              children: [
                const Text(
                  'Hekiminin belirlediği kullanım bilgilerini gir. Plan değişikliği geçmiş dozları değiştirmez.',
                ),
                const SizedBox(height: 20),
                TextFormField(
                  controller: _times,
                  enabled: !_busy && !_uncertain,
                  decoration: InputDecoration(
                    labelText: 'Günlük saatler',
                    helperText: 'Örnek biçim: 09:00, 21:00',
                    errorText: fieldError(_error, 'times'),
                  ),
                  validator: (v) {
                    final times = (v ?? '')
                        .split(',')
                        .map((e) => e.trim())
                        .toList();
                    if (times.length > 1440 ||
                        times.any(
                          (e) =>
                              !RegExp(r'^([01]\d|2[0-3]):[0-5]\d$').hasMatch(e),
                        )) {
                      return 'Saatleri HH:mm biçiminde virgülle ayır.';
                    }
                    return times.toSet().length != times.length
                        ? 'Aynı saati bir kez gir.'
                        : null;
                  },
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _quantity,
                  enabled: !_busy && !_uncertain,
                  keyboardType: const TextInputType.numberWithOptions(
                    decimal: true,
                  ),
                  decoration: InputDecoration(
                    labelText: 'Her dozun miktarı',
                    errorText: fieldError(_error, 'quantityPerDose'),
                  ),
                  validator: (v) => decimalError(v, positive: true),
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _zone,
                  enabled: !_busy && !_uncertain,
                  autocorrect: false,
                  decoration: InputDecoration(
                    labelText: 'Saat dilimi',
                    helperText: 'IANA bölgesi; örneğin Europe/Istanbul.',
                    errorText: fieldError(_error, 'timezone'),
                  ),
                  validator: (v) => v == null || v.trim().isEmpty
                      ? 'Saat dilimi girin.'
                      : null,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _start,
                  enabled: !_busy && !_uncertain,
                  decoration: InputDecoration(
                    labelText: 'Başlangıç tarihi',
                    helperText: 'YYYY-MM-DD',
                    errorText: fieldError(_error, 'startDate'),
                    suffixIcon: IconButton(
                      tooltip: 'Başlangıç tarihi seç',
                      onPressed: _busy || _uncertain
                          ? null
                          : () => _date(_start),
                      icon: const Icon(Icons.calendar_month),
                    ),
                  ),
                  validator: _dateError,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _end,
                  enabled: !_busy && !_uncertain,
                  decoration: InputDecoration(
                    labelText: 'Bitiş tarihi (isteğe bağlı)',
                    helperText: 'Bitiş günü plana dahildir.',
                    errorText: fieldError(_error, 'endDate'),
                    suffixIcon: IconButton(
                      tooltip: 'Bitiş tarihi seç',
                      onPressed: _busy || _uncertain ? null : () => _date(_end),
                      icon: const Icon(Icons.calendar_month),
                    ),
                  ),
                  validator: (v) {
                    final error = _dateError(v, optional: true);
                    if (error != null) return error;
                    return (v ?? '').trim().isNotEmpty &&
                            _dateError(_start.text) == null &&
                            v!.trim().compareTo(_start.text.trim()) < 0
                        ? 'Bitiş başlangıçtan önce olamaz.'
                        : null;
                  },
                ),
                ErrorNotice(_error),
                if (_uncertain) ...[
                  const Text(
                    'Plan kaydedilmiş olabilir. Yeni bir sürüm oluşturmadan önce plan geçmişini kontrol et.',
                  ),
                  TextButton(
                    onPressed: () {
                      refreshOnline(ref, medicationChanged: widget.id);
                      context.go('/medications/${widget.id}');
                    },
                    child: const Text('Plan geçmişini kontrol et'),
                  ),
                ],
                const SizedBox(height: 20),
                FilledButton(
                  onPressed: _busy || _uncertain ? null : _save,
                  child: Text(_busy ? 'Kaydediliyor…' : 'Planı kaydet'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
