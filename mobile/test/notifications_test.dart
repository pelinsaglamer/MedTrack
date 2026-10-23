import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:timezone/data/latest.dart' as zones;
import 'package:timezone/timezone.dart' as tz;
import 'package:medtrack/features/notifications/android_notification_adapter.dart';
import 'package:medtrack/features/notifications/notification_controller.dart';
import 'package:medtrack/features/notifications/notification_models.dart';
import 'package:medtrack/features/notifications/notification_ports.dart';

const ownerId = '11111111-1111-4111-8111-111111111111';
const dose = '22222222-2222-4222-8222-222222222222';
Reminder reminder({
  String id = dose,
  String medication = 'med',
  String name = 'Fictional sensitive medicine',
}) => Reminder.fixture(
  id: id,
  medicationId: medication,
  name: name,
  zone: 'Europe/Istanbul',
  at: DateTime.now().toUtc().add(const Duration(hours: 1)),
);

class FakeAdapter implements NotificationAdapter {
  @override
  bool supported = true;
  bool allowed = true,
      exact = true,
      restricted = false,
      failExactOnce = false,
      failCancel = false;
  int prompts = 0;
  void Function(String)? onOpen;
  final Map<int, ScheduledReminder> scheduled = {};
  final List<ScheduledReminder> requests = [];
  @override
  Future<void> initialize(void Function(String) callback) async =>
      onOpen = callback;
  @override
  Future<NotificationCapabilities> capabilities() async =>
      NotificationCapabilities(
        allowed: allowed,
        exact: exact,
        batteryRestricted: restricted,
      );
  @override
  Future<bool> requestPermission() async {
    prompts++;
    return allowed;
  }

  @override
  Future<void> requestExactPermission() async => exact = true;
  @override
  Future<void> schedule(ScheduledReminder value) async {
    if (failExactOnce && value.exact) {
      failExactOnce = false;
      exact = false;
      throw StateError('permission revoked');
    }
    requests.add(value);
    scheduled[value.notificationId] = value;
  }

  @override
  Future<void> cancel(int id) async => scheduled.remove(id);
  @override
  Future<void> cancelAll() async {
    if (failCancel) throw StateError('OS error');
    scheduled.clear();
  }

  @override
  Future<List<int>> pendingIds() async => scheduled.keys.toList();
  @override
  Future<void> openSettings() async {}
  @override
  Future<void> openBatterySettings() async {}
}

class MemoryNotificationStore implements NotificationStore {
  NotificationConfig config = NotificationConfig(owner: ownerId, enabled: true);
  bool fail = false;
  Completer<void>? delayedWrite;
  final writeStarted = Completer<void>();
  Completer<NotificationConfig>? delayedRead;
  @override
  Future<NotificationConfig> read() async =>
      delayedRead?.future ??
      Future.value(NotificationConfig.fromJson(config.toJson()));
  @override
  Future<void> write(NotificationConfig value) async {
    if (fail) throw StateError('storage');
    if (!writeStarted.isCompleted) writeStarted.complete();
    await delayedWrite?.future;
    config = NotificationConfig.fromJson(value.toJson());
  }

  @override
  Future<void> clear() async => config = NotificationConfig();
}

class FakeSource implements ReminderSource {
  String account = ownerId;
  List<Reminder> items = [reminder()];
  bool fail = false;
  Completer<ReminderBatch>? pending;
  final started = Completer<void>();
  @override
  Future<String> owner() async => account;
  @override
  Future<ReminderBatch> fetch() async {
    if (!started.isCompleted) started.complete();
    if (fail) throw StateError('offline');
    return pending?.future ??
        Future.value(
          ReminderBatch(
            account,
            items,
            DateTime.now().toUtc().add(const Duration(days: 14)),
            false,
          ),
        );
  }
}

ProviderContainer setup(
  FakeAdapter adapter,
  MemoryNotificationStore store,
  FakeSource source,
) {
  final c = ProviderContainer(
    overrides: [
      notificationAdapterProvider.overrideWithValue(adapter),
      notificationStoreProvider.overrideWithValue(store),
      reminderSourceProvider.overrideWithValue(source),
    ],
  );
  c.read(notificationControllerProvider);
  return c;
}

void main() {
  test('stable IDs survive refresh and collision resolution without replacing another dose', () {
    final ids = <String, int>{};
    final a = reserveNotificationId(ownerId, dose, ids, hash: (_) => 7);
    final b = reserveNotificationId(ownerId, 'other', ids, hash: (_) => 7);
    expect(a, 7);
    expect(b, 8);
    expect(reserveNotificationId(ownerId, dose, ids, hash: (_) => 99), 7);
    expect(
      stableNotificationHash('$ownerId/$dose'),
      stableNotificationHash('$ownerId/$dose'),
    );
  });
  test('UTC instants keep intended zone through DST gap and overlap', () {
    zones.initializeTimeZones();
    final berlin = tz.getLocation('Europe/Berlin');
    final gap = tz.TZDateTime.from(
      DateTime.parse('2026-03-29T01:30:00Z'),
      berlin,
    );
    expect(gap.hour, 3);
    expect(gap.toUtc(), DateTime.parse('2026-03-29T01:30:00Z'));
    final overlap = tz.TZDateTime.from(
      DateTime.parse('2026-10-25T00:30:00Z'),
      berlin,
    );
    expect(overlap.hour, 2);
    expect(overlap.timeZoneOffset, const Duration(hours: 2));
  });
  test(
    'activate schedules privately without automatically requesting permission',
    () async {
      final adapter = FakeAdapter();
      final store = MemoryNotificationStore();
      final source = FakeSource();
      final c = setup(adapter, store, source);
      addTearDown(c.dispose);
      await c.read(notificationControllerProvider.notifier).activate();
      expect(adapter.prompts, 0);
      expect(adapter.scheduled, hasLength(1));
      expect(adapter.requests.single.title, isNot(contains('Fictional')));
      expect(adapter.requests.single.payload, isNot(contains('medicine')));
      expect(c.read(notificationControllerProvider).count, 1);
    },
  );
  test('permission denial cancels reminders without fetching or blocking domain data', () async {
    final adapter = FakeAdapter()..allowed = false;
    final store = MemoryNotificationStore();
    final source = FakeSource();
    final c = setup(adapter, store, source);
    addTearDown(c.dispose);
    await c.read(notificationControllerProvider.notifier).enable();
    expect(adapter.prompts, 1);
    expect(source.started.isCompleted, isFalse);
    expect(adapter.scheduled, isEmpty);
    expect(c.read(notificationControllerProvider).allowed, isFalse);
  });
  test('exact permission absent uses inexact scheduling', () async {
    final adapter = FakeAdapter()..exact = false;
    final c = setup(adapter, MemoryNotificationStore(), FakeSource());
    addTearDown(c.dispose);
    await c.read(notificationControllerProvider.notifier).activate();
    expect(adapter.requests.single.exact, isFalse);
  });
  test(
    'revocation during scheduling falls back instead of losing every reminder',
    () async {
      final adapter = FakeAdapter()..failExactOnce = true;
      final c = setup(adapter, MemoryNotificationStore(), FakeSource());
      addTearDown(c.dispose);
      await c.read(notificationControllerProvider.notifier).activate();
      expect(adapter.requests.single.exact, isFalse);
      expect(c.read(notificationControllerProvider).error, isNull);
    },
  );
  test(
    'refresh replaces future IDs after a schedule edit; no duplicates',
    () async {
      final adapter = FakeAdapter();
      final store = MemoryNotificationStore();
      final source = FakeSource();
      final c = setup(adapter, store, source);
      addTearDown(c.dispose);
      final controller = c.read(notificationControllerProvider.notifier);
      await controller.activate();
      final original = adapter.scheduled.keys.single;
      await controller.sync();
      expect(adapter.scheduled.keys.single, original);
      source.items = [reminder(id: '33333333-3333-4333-8333-333333333333')];
      await controller.sync(medicationChanged: 'med');
      expect(adapter.scheduled, hasLength(1));
      expect(adapter.scheduled.containsKey(original), isFalse);
    },
  );
  test('archive/dose invalidation cancels known stale reminder even when refresh is offline', () async {
    final adapter = FakeAdapter();
    final source = FakeSource();
    final c = setup(adapter, MemoryNotificationStore(), source);
    addTearDown(c.dispose);
    final controller = c.read(notificationControllerProvider.notifier);
    await controller.activate();
    source.fail = true;
    await controller.sync(medicationChanged: 'med');
    expect(adapter.scheduled, isEmpty);
    expect(c.read(notificationControllerProvider).error, isNotNull);
  });
  test('permission revocation removes previously scheduled alarms', () async {
    final adapter = FakeAdapter();
    final c = setup(adapter, MemoryNotificationStore(), FakeSource());
    addTearDown(c.dispose);
    final controller = c.read(notificationControllerProvider.notifier);
    await controller.activate();
    adapter.allowed = false;
    await controller.sync();
    expect(adapter.scheduled, isEmpty);
  });
  test(
    'privacy change cancels old content and reschedules with same ID',
    () async {
      final adapter = FakeAdapter();
      final c = setup(adapter, MemoryNotificationStore(), FakeSource());
      addTearDown(c.dispose);
      final controller = c.read(notificationControllerProvider.notifier);
      await controller.activate();
      final id = adapter.scheduled.keys.single;
      await controller.setRedacted(false);
      expect(adapter.scheduled.keys.single, id);
      expect(
        adapter.scheduled.values.single.title,
        'Fictional sensitive medicine',
      );
      await controller.setRedacted(true);
      expect(
        adapter.scheduled.values.single.title,
        isNot(contains('Fictional')),
      );
    },
  );
  test(
    'logout interrupts a fetch without a cleanup deadlock or late re-schedule',
    () async {
      final adapter = FakeAdapter();
      final store = MemoryNotificationStore();
      final source = FakeSource()..pending = Completer<ReminderBatch>();
      final c = setup(adapter, store, source);
      addTearDown(c.dispose);
      final controller = c.read(notificationControllerProvider.notifier);
      final active = controller.activate();
      await source.started.future;
      await controller.endSession().timeout(const Duration(seconds: 1));
      source.pending!.complete(
        ReminderBatch(
          ownerId,
          [reminder()],
          DateTime.now().add(const Duration(days: 14)),
          false,
        ),
      );
      await active;
      expect(adapter.scheduled, isEmpty);
      expect(store.config.owner, isNull);
    },
  );
  test(
    'different account cancels old alarms and requires a fresh opt-in',
    () async {
      final adapter = FakeAdapter();
      final store = MemoryNotificationStore();
      final source = FakeSource();
      final c = setup(adapter, store, source);
      addTearDown(c.dispose);
      final controller = c.read(notificationControllerProvider.notifier);
      await controller.activate();
      source.account = 'different-owner';
      await controller.activate();
      expect(adapter.scheduled, isEmpty);
      expect(store.config.enabled, isFalse);
    },
  );
  test(
    'taps only open the verified account dose and never mutate it',
    () async {
      final adapter = FakeAdapter();
      final c = setup(adapter, MemoryNotificationStore(), FakeSource());
      addTearDown(c.dispose);
      final controller = c.read(notificationControllerProvider.notifier);
      await controller.activate();
      adapter.onOpen!('{"owner":"someone-else","dose":"$dose"}');
      expect(c.read(notificationControllerProvider).openDose, isNull);
      adapter.onOpen!('{"owner":"$ownerId","dose":"$dose"}');
      expect(c.read(notificationControllerProvider).openDose, dose);
      controller.acknowledgeOpen();
      await controller.endSession();
      adapter.onOpen!('{"owner":"$ownerId","dose":"$dose"}');
      expect(c.read(notificationControllerProvider).openDose, isNull);
    },
  );
  test('storage failure prevents new OS jobs whose identity could not be persisted', () async {
    final adapter = FakeAdapter();
    final store = MemoryNotificationStore()..fail = true;
    final c = setup(adapter, store, FakeSource());
    addTearDown(c.dispose);
    await c.read(notificationControllerProvider.notifier).activate();
    expect(adapter.scheduled, isEmpty);
    expect(c.read(notificationControllerProvider).error, isNotNull);
  });
  test('login activation does not discard preferences while secure cache is loading', () async {
    final adapter = FakeAdapter();
    final store = MemoryNotificationStore()
      ..delayedRead = Completer<NotificationConfig>();
    final c = setup(adapter, store, FakeSource());
    addTearDown(c.dispose);
    await Future<void>.delayed(Duration.zero);
    final activate = c.read(notificationControllerProvider.notifier).activate();
    store.delayedRead!.complete(
      NotificationConfig(owner: ownerId, enabled: true),
    );
    await activate;
    expect(c.read(notificationControllerProvider).enabled, isTrue);
    expect(adapter.scheduled, isNotEmpty);
  });
  test(
    'logout prevents a late secure-cache read from restoring an old opt-in',
    () async {
      final adapter = FakeAdapter();
      final store = MemoryNotificationStore()
        ..delayedRead = Completer<NotificationConfig>();
      final c = setup(adapter, store, FakeSource());
      addTearDown(c.dispose);
      await Future<void>.delayed(Duration.zero);
      final controller = c.read(notificationControllerProvider.notifier);
      await controller.endSession();
      store.delayedRead!.complete(
        NotificationConfig(owner: ownerId, enabled: true),
      );
      await controller.refreshCapabilities();
      expect(c.read(notificationControllerProvider).enabled, isFalse);
      expect(adapter.scheduled, isEmpty);
    },
  );
  test('platform UTC scheduling preserves the later instant in a repeated DST hour', () {
    final later = DateTime.parse('2026-10-25T01:30:00Z');
    expect(platformNotificationTime(later).toUtc(), later);
    expect(platformNotificationTime(later).timeZoneOffset, Duration.zero);
  });
  test(
    'a late persistent write cannot restore reminder ownership after logout',
    () async {
      final adapter = FakeAdapter();
      final store = MemoryNotificationStore()..delayedWrite = Completer<void>();
      final c = setup(adapter, store, FakeSource());
      addTearDown(c.dispose);
      final controller = c.read(notificationControllerProvider.notifier);
      final active = controller.activate();
      await store.writeStarted.future;
      await controller.endSession();
      store.delayedWrite!.complete();
      await active;
      expect(store.config.owner, isNull);
      expect(adapter.scheduled, isEmpty);
    },
  );
}
