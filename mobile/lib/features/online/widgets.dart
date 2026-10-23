import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../shared/design.dart';

class AsyncPanel<T> extends StatelessWidget {
  const AsyncPanel({
    super.key,
    required this.value,
    required this.onRetry,
    required this.builder,
  });
  final AsyncValue<T> value;
  final VoidCallback onRetry;
  final Widget Function(T) builder;
  @override
  Widget build(BuildContext context) => value.when(
    loading: () => const Center(child: CircularProgressIndicator()),
    error: (e, _) => FailureView(
      message: e is ApiFailure ? e.message : 'Veriler yüklenemedi.',
      onRetry: onRetry,
    ),
    data: builder,
  );
}

class ErrorNotice extends StatelessWidget {
  const ErrorNotice(this.error, {super.key});
  final Object? error;
  @override
  Widget build(BuildContext context) => error == null
      ? const SizedBox.shrink()
      : Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Semantics(
            liveRegion: true,
            child: Text(
              error is ApiFailure ? (error as ApiFailure).message : 'İşlem tamamlanamadı. Verileri kontrol edip tekrar deneyin.',
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        );
}

class MainNavigation extends StatelessWidget {
  const MainNavigation(this.selected, {super.key});
  final int selected;
  @override
  Widget build(BuildContext context) => NavigationBar(
    selectedIndex: selected,
    onDestinationSelected: (i) =>
        context.go(['/medications', '/today', '/summary'][i]),
    destinations: const [
      NavigationDestination(
        icon: Icon(Icons.medication_outlined),
        label: 'İlaçlarım',
      ),
      NavigationDestination(icon: Icon(Icons.today_outlined), label: 'Günlük'),
      NavigationDestination(
        icon: Icon(Icons.bar_chart_outlined),
        label: 'Haftalık',
      ),
    ],
  );
}

class PageControls extends StatelessWidget {
  const PageControls({
    super.key,
    required this.page,
    required this.total,
    required this.onChange,
  });
  final int page, total;
  final ValueChanged<int> onChange;
  @override
  Widget build(BuildContext context) => total <= 1 && page == 0
      ? const SizedBox.shrink()
      : Wrap(
          alignment: WrapAlignment.center,
          crossAxisAlignment: WrapCrossAlignment.center,
          spacing: 12,
          children: [
            OutlinedButton(
              onPressed: page > 0 ? () => onChange(page - 1) : null,
              child: const Text('Önceki'),
            ),
            Text('Sayfa ${page + 1} / ${total == 0 ? 1 : total}'),
            OutlinedButton(
              onPressed: page + 1 < total ? () => onChange(page + 1) : null,
              child: const Text('Sonraki'),
            ),
          ],
        );
}

Future<bool> confirm(
  BuildContext context,
  String title,
  String message,
) async =>
    await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Vazgeç'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Onayla'),
          ),
        ],
      ),
    ) ??
    false;
String? fieldError(Object? error, String name) =>
    error is ApiFailure ? error.fields[name] : null;
