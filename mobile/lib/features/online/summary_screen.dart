import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../shared/design.dart';
import 'data.dart';
import 'models.dart';
import 'widgets.dart';

class SummaryScreen extends ConsumerStatefulWidget {
  const SummaryScreen({super.key});
  @override
  ConsumerState<SummaryScreen> createState() => _SummaryState();
}

class _SummaryState extends ConsumerState<SummaryScreen> {
  String _week = '';
  void _move(String from, int days) => setState(
    () =>
        _week = calendarDate(from)
            .add(Duration(days: days))
            .toIso8601String()
            .substring(0, 10),
  );
  @override
  Widget build(BuildContext context) {
    final value = ref.watch(weeklyProvider(_week));
    return Scaffold(
      appBar: AppBar(
        title: const Text('Haftalık özet'),
        actions: [
          IconButton(
            tooltip: 'Yenile',
            onPressed: () => ref.invalidate(weeklyProvider),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      bottomNavigationBar: const MainNavigation(2),
      body: ContentFrame(
        child: AsyncPanel(
          value: value,
          onRetry: () => ref.invalidate(weeklyProvider(_week)),
          builder: (s) => ListView(
            children: [
              Wrap(
                alignment: WrapAlignment.center,
                spacing: 12,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  IconButton(
                    tooltip: 'Önceki hafta',
                    onPressed: () => _move(s.start, -7),
                    icon: const Icon(Icons.chevron_left),
                  ),
                  Text('${displayDate(s.start)} haftası'),
                  if (_week.isNotEmpty)
                    IconButton(
                      tooltip: 'Bu hafta',
                      onPressed: () => setState(() => _week = ''),
                      icon: const Icon(Icons.today),
                    ),
                ],
              ),
              Text('Hesap saat dilimi: ${s.zone}'),
              const SizedBox(height: 12),
              for (final item in [
                ('Planlanan', s.total),
                ('Alındı', s.taken),
                ('Atlandı', s.skipped),
                ('Zamanında teyit yok', s.missed),
                ('Teyit bekliyor', s.awaiting),
                ('Gelecekte planlandı', s.pending),
              ])
                Card(
                  child: ListTile(
                    title: Text(item.$1),
                    trailing: Text(
                      '${item.$2}',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                ),
              const SizedBox(height: 12),
              Text('Sonraki doz zamanına kadar teyit girilmeyen: ${s.late}'),
              const Text(
                'Bu ek sayı daha sonra teyit edilen dozları da içerebilir; diğer sayılara eklenmez. Teyit olmaması ilacın alınmadığını kanıtlamaz.',
              ),
            ],
          ),
        ),
      ),
    );
  }
}
