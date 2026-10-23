import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../shared/design.dart';
import 'data.dart';
import 'models.dart';
import 'widgets.dart';

class DashboardScreen extends ConsumerStatefulWidget {
  const DashboardScreen({super.key});
  @override
  ConsumerState<DashboardScreen> createState() => _DashboardState();
}

class _DashboardState extends ConsumerState<DashboardScreen> {
  String _date = '';
  int _page = 0;
  void _move(String current, int days) {
    setState(() {
      _date = calendarDate(current)
          .add(Duration(days: days))
          .toIso8601String()
          .substring(0, 10);
      _page = 0;
    });
  }

  Future<void> _pick(String current) async {
    try {
      final today = await ref.read(dailyProvider(('', 0)).future);
      if (!mounted) return;
      final d = await showDatePicker(
        context: context,
        initialDate: calendarDate(current),
        firstDate: DateTime(1),
        lastDate: calendarDate(today.date).add(const Duration(days: 13)),
      );
      if (d != null) {
        setState(() {
          _date = d.toIso8601String().substring(0, 10);
          _page = 0;
        });
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Tarih bilgisi alınamadı. Bağlantını kontrol edip tekrar dene.',
            ),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final result = ref.watch(dailyProvider((_date, _page)));
    return Scaffold(
      appBar: AppBar(
        title: const Text('Günlük dozlar'),
        actions: [
          IconButton(
            tooltip: 'Bugüne dön',
            onPressed: () => setState(() {
              _date = '';
              _page = 0;
            }),
            icon: const Icon(Icons.today),
          ),
          IconButton(
            tooltip: 'Yenile',
            onPressed: () => ref.invalidate(dailyProvider),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      bottomNavigationBar: const MainNavigation(1),
      body: ContentFrame(
        child: AsyncPanel(
          value: result,
          onRetry: () => ref.invalidate(dailyProvider((_date, _page))),
          builder: (data) => RefreshIndicator(
            onRefresh: () async {
              ref.invalidate(dailyProvider);
              try {
                await ref.read(dailyProvider((_date, _page)).future);
              } catch (_) {}
            },
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                Wrap(
                  alignment: WrapAlignment.spaceBetween,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    IconButton(
                      tooltip: 'Önceki gün',
                      onPressed: () => _move(data.date, -1),
                      icon: const Icon(Icons.chevron_left),
                    ),
                    TextButton(
                      onPressed: () => _pick(data.date),
                      child: Text(displayDate(data.date)),
                    ),
                    IconButton(
                      tooltip: 'Sonraki gün',
                      onPressed: () => _move(data.date, 1),
                      icon: const Icon(Icons.chevron_right),
                    ),
                    TextButton(
                      onPressed: () => setState(() {
                        _date = '';
                        _page = 0;
                      }),
                      child: const Text('Bugün'),
                    ),
                  ],
                ),
                Text('Gün sınırı: ${data.zone}'),
                const SizedBox(height: 12),
                const Text(
                  '“Zamanında teyit yok” ilacın alınmadığını kanıtlamaz. Aldım/Atladım yalnız senin seçiminle kaydedilir.',
                ),
                if (data.items.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(32),
                    child: Text(
                      'Bu gün için planlanmış doz yok.',
                      textAlign: TextAlign.center,
                    ),
                  ),
                for (final dose in data.items)
                  Card(
                    child: ListTile(
                      leading: Text(
                        dose.time,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      title: Text(dose.name),
                      subtitle: Text(
                        '${dose.quantity} ${dose.unit} • ${statusLabel(dose.status)}\nPlan: ${dose.zone}',
                      ),
                      isThreeLine: true,
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => context.push('/today/doses/${dose.id}'),
                    ),
                  ),
                PageControls(
                  page: _page,
                  total: data.totalPages,
                  onChange: (p) => setState(() => _page = p),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
