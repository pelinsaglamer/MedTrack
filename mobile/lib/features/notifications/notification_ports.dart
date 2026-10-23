import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../auth/session.dart';
import '../online/models.dart';
import 'notification_models.dart';

abstract interface class NotificationAdapter {
  bool get supported;
  Future<void> initialize(void Function(String) onOpen);
  Future<NotificationCapabilities> capabilities();
  Future<bool> requestPermission();
  Future<void> requestExactPermission();
  Future<void> schedule(ScheduledReminder reminder);
  Future<void> cancel(int id);
  Future<void> cancelAll();
  Future<List<int>> pendingIds();
  Future<void> openSettings();
  Future<void> openBatterySettings();
}

abstract interface class NotificationStore {
  Future<NotificationConfig> read();
  Future<void> write(NotificationConfig config);
  Future<void> clear();
}

class SecureNotificationStore implements NotificationStore {
  SecureNotificationStore(this.storage);
  final FlutterSecureStorage storage;
  static const key = 'medtrack.notifications.v1';
  @override
  Future<NotificationConfig> read() async {
    final raw = await storage.read(key: key);
    return raw == null
        ? NotificationConfig()
        : NotificationConfig.fromJson(jsonDecode(raw) as Json);
  }

  @override
  Future<void> write(NotificationConfig config) =>
      storage.write(key: key, value: jsonEncode(config.toJson()));
  @override
  Future<void> clear() => storage.delete(key: key);
}

final notificationStoreProvider = Provider<NotificationStore>(
  (ref) => SecureNotificationStore(const FlutterSecureStorage()),
);

class ReminderBatch {
  const ReminderBatch(this.owner, this.reminders, this.until, this.limited);
  final String owner;
  final List<Reminder> reminders;
  final DateTime until;
  final bool limited;
}

abstract interface class ReminderSource {
  Future<String> owner();
  Future<ReminderBatch> fetch();
}

final reminderSourceProvider = Provider<ReminderSource>(
  (ref) => ServerReminderSource(ref.read(sessionProvider.notifier)),
);

class ServerReminderSource implements ReminderSource {
  ServerReminderSource(this.session);
  final SessionController session;
  @override
  Future<String> owner() async =>
      (await session.authorized('GET', '/account/me'))['id'] as String;
  @override
  Future<ReminderBatch> fetch() async {
    final account = await owner();
    final reminders = <Reminder>[];
    DateTime? until;
    bool limited = false;
    for (var page = 0; page < 2; page++) {
      final json = await session.authorized(
        'GET',
        '/doses/reminders?page=$page&size=100',
      );
      until = calendarDate(json['date'] as String)
          .add(const Duration(days: 14));
      reminders.addAll(
        (json['content'] as List).map((e) => Reminder(Dose(e as Json))),
      );
      limited = (json['totalElements'] as int) > 200;
      if (page + 1 >= (json['totalPages'] as int)) break;
    }
    return ReminderBatch(account, reminders, until!, limited);
  }
}
