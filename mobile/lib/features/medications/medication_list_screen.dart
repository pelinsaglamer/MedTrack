import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../shared/design.dart';
import '../auth/session.dart';
import 'medication.dart';
import '../online/data.dart';
import '../online/widgets.dart';

class MedicationListScreen extends ConsumerStatefulWidget {
  const MedicationListScreen({super.key});
  @override
  ConsumerState<MedicationListScreen> createState() =>
      _MedicationListScreenState();
}

class _MedicationListScreenState extends ConsumerState<MedicationListScreen> {
  int _page = 0;
  bool _signingOut = false;
  Future<void> _logout() async {
    setState(() => _signingOut = true);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(sessionProvider.notifier).logout();
    } on ApiFailure catch (e) {
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            e.code == 'SECURE_STORAGE' ? e.message : 'Cihazdaki oturum kapatıldı. Sunucu çıkışı doğrulanamadı; sunucudaki oturum süresi dolana kadar açık kalabilir.',
          ),
        ),
      );
    } finally {
      if (mounted) setState(() => _signingOut = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final result = ref.watch(medicationsProvider(_page));
    return Scaffold(
      bottomNavigationBar: const MainNavigation(0),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/medications/new'),
        icon: const Icon(Icons.add),
        label: const Text('İlaç ekle'),
      ),
      appBar: AppBar(
        title: const Text('İlaçlarım'),
        actions: [
          IconButton(
            tooltip: 'Çıkış yap',
            onPressed: _signingOut ? null : _logout,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: ContentFrame(
        child: result.when(
          loading: () => const Center(
            child: CircularProgressIndicator(
              semanticsLabel: 'İlaçlar yükleniyor',
            ),
          ),
          error: (error, _) => FailureView(
            message: error is ApiFailure ? error.message : 'Liste yüklenemedi.',
            onRetry: () => ref.invalidate(medicationsProvider(_page)),
          ),
          data: (data) => RefreshIndicator(
            onRefresh: () async {
              ref.invalidate(medicationsProvider(_page));
              ref.invalidate(inventoryProvider);
              try {
                await ref.read(medicationsProvider(_page).future);
              } catch (_) {
                // The provider renders the error and retry action.
              }
            },
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                Text(
                  'Aktif ilaçların',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                const Text('Listeyi yenilemek için aşağı çekebilirsin.'),
                const SizedBox(height: 20),
                if (data.items.isEmpty)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 60),
                    child: Column(
                      children: [
                        Icon(Icons.medication_outlined, size: 48),
                        SizedBox(height: 16),
                        Text(
                          'Henüz aktif ilaç kaydın yok.',
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                for (final medication in data.items)
                  Card(
                    child: ListTile(
                      leading: const Icon(Icons.medication_outlined),
                      title: Text(medication.name),
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Birim: ${medication.unit}'),
                          StockBadge(medication.id),
                        ],
                      ),
                      onTap: () =>
                          context.push('/medications/${medication.id}'),
                    ),
                  ),
                if (data.totalPages > 1 || _page > 0)
                  Padding(
                    padding: const EdgeInsets.only(top: 20),
                    child: Wrap(
                      alignment: WrapAlignment.center,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      spacing: 12,
                      children: [
                        OutlinedButton(
                          onPressed: _page > 0
                              ? () => setState(() => _page--)
                              : null,
                          child: const Text('Önceki'),
                        ),
                        Text(
                          'Sayfa ${_page + 1} / ${data.totalPages == 0 ? 1 : data.totalPages}',
                        ),
                        OutlinedButton(
                          onPressed: _page + 1 < data.totalPages
                              ? () => setState(() => _page++)
                              : null,
                          child: const Text('Sonraki'),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class StockBadge extends ConsumerWidget {
  const StockBadge(this.id, {super.key});
  final String id;
  @override
  Widget build(BuildContext context, WidgetRef ref) => ref
      .watch(inventoryProvider((id, 0)))
      .when(
        loading: () => const Text('Stok yükleniyor…'),
        error: (_, _) => const Text('Stok yüklenemedi'),
        data: (v) =>
            Text(v.known ? 'Stok: ${v.quantity} ${v.unit}' : 'Stok bilinmiyor'),
      );
}
