import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/timezone.dart' as tz;

import 'notification_models.dart';
import 'notification_ports.dart';

final notificationAdapterProvider = Provider<NotificationAdapter>(
  (ref) => AndroidNotificationAdapter(),
);

class AndroidNotificationAdapter implements NotificationAdapter {
  final plugin = FlutterLocalNotificationsPlugin();
  static const channelId = 'medtrack_reminders_v1';
  static const _settings = MethodChannel(
    'com.medtrack.medtrack/notification_settings',
  );
  AndroidFlutterLocalNotificationsPlugin get _android => plugin
      .resolvePlatformSpecificImplementation<
        AndroidFlutterLocalNotificationsPlugin
      >()!;
  @override
  bool get supported => Platform.isAndroid;
  @override
  Future<void> initialize(void Function(String) onOpen) async {
    if (!supported) return;
    await plugin.initialize(
      settings: const InitializationSettings(
        android: AndroidInitializationSettings('ic_notification'),
      ),
      onDidReceiveNotificationResponse: (r) {
        if (r.payload != null) onOpen(r.payload!);
      },
    );
    await _android.createNotificationChannel(
      const AndroidNotificationChannel(
        channelId,
        'İlaç hatırlatmaları',
        description: 'Planlı dozlar için cihaz hatırlatmaları',
        importance: Importance.high,
      ),
    );
    final launch = await plugin.getNotificationAppLaunchDetails();
    if (launch?.didNotificationLaunchApp == true &&
        launch?.notificationResponse?.payload != null) {
      onOpen(launch!.notificationResponse!.payload!);
    }
  }

  @override
  Future<NotificationCapabilities> capabilities() async {
    if (!supported) {
      return const NotificationCapabilities(
        allowed: false,
        exact: false,
        batteryRestricted: false,
      );
    }
    final channels = await _android.getNotificationChannels();
    final blocked =
        channels?.any(
          (c) => c.id == channelId && c.importance == Importance.none,
        ) ??
        false;
    return NotificationCapabilities(
      allowed: (await _android.areNotificationsEnabled() ?? false) && !blocked,
      exact: await _android.canScheduleExactNotifications() ?? false,
      batteryRestricted:
          !(await _settings.invokeMethod<bool>('batteryExempt') ?? false),
    );
  }

  @override
  Future<bool> requestPermission() async =>
      await _android.requestNotificationsPermission() ?? false;
  @override
  Future<void> requestExactPermission() async {
    await _android.requestExactAlarmsPermission();
  }

  @override
  Future<void> schedule(ScheduledReminder reminder) async {
    await plugin.zonedSchedule(
      id: reminder.notificationId,
      title: reminder.title,
      body: reminder.body,
      payload: reminder.payload,
      scheduledDate: platformNotificationTime(reminder.reminder.at),
      notificationDetails: const NotificationDetails(
        android: AndroidNotificationDetails(
          channelId,
          'İlaç hatırlatmaları',
          channelDescription: 'Planlı dozlar için cihaz hatırlatmaları',
          icon: 'ic_notification',
          importance: Importance.high,
          priority: Priority.high,
          visibility: NotificationVisibility.private,
        ),
      ),
      androidScheduleMode: reminder.exact
          ? AndroidScheduleMode.exactAllowWhileIdle
          : AndroidScheduleMode.inexactAllowWhileIdle,
    );
  }

  @override
  Future<void> cancel(int id) => plugin.cancel(id: id);
  @override
  Future<void> cancelAll() => plugin.cancelAll();
  @override
  Future<List<int>> pendingIds() async =>
      (await plugin.pendingNotificationRequests()).map((e) => e.id).toList();
  @override
  Future<void> openSettings() =>
      _settings.invokeMethod<void>('openNotifications');
  @override
  Future<void> openBatterySettings() =>
      _settings.invokeMethod<void>('openBattery');
}

// The server already resolved IANA/DST. UTC prevents a second ambiguous local-time resolution.
tz.TZDateTime platformNotificationTime(DateTime instant) =>
    tz.TZDateTime.from(instant.toUtc(), tz.UTC);
