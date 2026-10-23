import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../core/api_client.dart';
import '../auth/session.dart';
import '../medications/medication.dart';
import 'models.dart';

final onlineApiProvider = Provider<OnlineApi>((ref) {
  ref.watch(sessionProvider);
  return OnlineApi(ref.read(sessionProvider.notifier));
});

class OnlineApi {
  OnlineApi(this.session);
  final SessionController session;
  Future<Json> request(
    String method,
    String path, {
    Json? body,
    Map<String, String> headers = const {},
  }) => session.authorized(method, path, body: body, headers: headers);
  Future<Medication> medication(String id) async =>
      Medication.fromJson(await request('GET', '/medications/$id'));
  Future<DailyPage> daily(String date, int page) async => DailyPage(
    await request(
      'GET',
      '/doses?page=$page&size=20${date.isEmpty ? '' : '&date=$date'}',
    ),
  );
}

final medicationProvider = FutureProvider.autoDispose
    .family<Medication, String>(
      (ref, id) => ref.watch(onlineApiProvider).medication(id),
    );
final dailyProvider = FutureProvider.autoDispose
    .family<DailyPage, (String, int)>(
      (ref, key) => ref.watch(onlineApiProvider).daily(key.$1, key.$2),
    );
final doseProvider = FutureProvider.autoDispose.family<Dose, String>(
  (ref, id) async =>
      Dose(await ref.watch(onlineApiProvider).request('GET', '/doses/$id')),
);
final schedulesProvider = FutureProvider.autoDispose
    .family<PageData<SchedulePlan>, (String, int)>(
      (ref, key) async => PageData(
        await ref
            .watch(onlineApiProvider)
            .request(
              'GET',
              '/medications/${key.$1}/schedules?page=${key.$2}&size=20',
            ),
        SchedulePlan.new,
      ),
    );
final inventoryProvider = FutureProvider.autoDispose
    .family<InventoryView, (String, int)>(
      (ref, key) async => InventoryView(
        await ref
            .watch(onlineApiProvider)
            .request('GET', '/inventory/${key.$1}?page=${key.$2}&size=20'),
      ),
    );
final actionsProvider = FutureProvider.autoDispose
    .family<PageData<ActionEvent>, (String, int)>(
      (ref, key) async => PageData(
        await ref
            .watch(onlineApiProvider)
            .request('GET', '/doses/${key.$1}/actions?page=${key.$2}&size=20'),
        ActionEvent.new,
      ),
    );
final weeklyProvider = FutureProvider.autoDispose.family<WeeklySummary, String>(
  (ref, start) async => WeeklySummary(
    await ref
        .watch(onlineApiProvider)
        .request(
          'GET',
          '/doses/summary${start.isEmpty ? '' : '?weekStart=$start'}',
        ),
  ),
);
final accountProvider = FutureProvider.autoDispose<Json>(
  (ref) => ref.watch(onlineApiProvider).request('GET', '/account/me'),
);

class PendingOperation {
  PendingOperation(this.path, Json input, {String? key})
    : key = key ?? const Uuid().v4(),
      input = Map.unmodifiable(input);
  final String path, key;
  final Json input;
  Json get body => {...input, 'clientOperationId': key};
  bool busy = false;
}

final operationsProvider =
    NotifierProvider<Operations, Map<String, PendingOperation>>(Operations.new);

class Operations extends Notifier<Map<String, PendingOperation>> {
  @override
  Map<String, PendingOperation> build() {
    ref.watch(sessionProvider.select((s) => s.epoch));
    return {};
  }

  PendingOperation begin(String scope, String path, Json body) {
    final existing = state[scope];
    if (existing != null) return existing;
    final op = PendingOperation(path, body);
    state = {...state, scope: op};
    return op;
  }

  Future<Json?> send(String scope) async {
    final op = state[scope];
    if (op == null || op.busy) return null;
    op.busy = true;
    state = {...state};
    final epoch = ref.read(sessionProvider).epoch;
    try {
      final result = await ref
          .read(onlineApiProvider)
          .request(
            'POST',
            op.path,
            body: op.body,
            headers: {'Idempotency-Key': op.key},
          );
      if (ref.read(sessionProvider).epoch == epoch) {
        state = {...state}..remove(scope);
      }
      return result;
    } on ApiFailure catch (e) {
      if (e.status != null &&
          e.status! >= 400 &&
          e.status! < 500 &&
          e.status != 408 &&
          e.status != 429) {
        if (ref.read(sessionProvider).epoch == epoch) {
          state = {...state}..remove(scope);
        }
      }
      rethrow;
    } finally {
      op.busy = false;
      if (ref.read(sessionProvider).epoch == epoch) state = {...state};
    }
  }
}

void refreshOnline(WidgetRef ref) {
  ref.invalidate(medicationsProvider);
  ref.invalidate(medicationProvider);
  ref.invalidate(dailyProvider);
  ref.invalidate(doseProvider);
  ref.invalidate(schedulesProvider);
  ref.invalidate(inventoryProvider);
  ref.invalidate(actionsProvider);
  ref.invalidate(weeklyProvider);
}
