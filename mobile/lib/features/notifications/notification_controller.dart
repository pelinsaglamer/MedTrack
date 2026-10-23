import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'android_notification_adapter.dart';
import 'notification_models.dart';
import 'notification_ports.dart';

class NotificationState {
  const NotificationState({
    this.supported = true,
    this.busy = false,
    this.enabled = false,
    this.redacted = true,
    this.allowed = false,
    this.exact = false,
    this.batteryRestricted = false,
    this.count = 0,
    this.limited = false,
    this.error,
    this.until,
    this.openDose,
  });
  final bool supported,
      busy,
      enabled,
      redacted,
      allowed,
      exact,
      batteryRestricted,
      limited;
  final int count;
  final String? error, openDose;
  final DateTime? until;
}

final notificationControllerProvider =
    NotifierProvider<NotificationController, NotificationState>(
      NotificationController.new,
    );

class NotificationController extends Notifier<NotificationState> {
  NotificationAdapter get _adapter => ref.read(notificationAdapterProvider);
  NotificationStore get _store => ref.read(notificationStoreProvider);
  ReminderSource get _source => ref.read(reminderSourceProvider);
  NotificationConfig _config = NotificationConfig();
  NotificationCapabilities _caps = const NotificationCapabilities(
    allowed: false,
    exact: false,
    batteryRestricted: false,
  );
  Future<void> _tail = Future.value();
  bool _initialized = false;
  int _generation = 0, _count = 0;
  bool _limited = false;
  DateTime? _until;
  String? _error, _tap, _activeOwner;
  bool _disposed = false;
  int _sessionReset = 0;
  @override
  NotificationState build() {
    ref.onDispose(() {
      _disposed = true;
      ++_generation;
    });
    Future.microtask(refreshCapabilities);
    return NotificationState(supported: _adapter.supported);
  }

  void _publish({bool busy = false, String? openDose}) {
    if (_disposed) return;
    state = NotificationState(
      supported: _adapter.supported,
      busy: busy,
      enabled: _config.enabled,
      redacted: _config.redacted,
      allowed: _caps.allowed,
      exact: _caps.exact,
      batteryRestricted: _caps.batteryRestricted,
      count: _count,
      limited: _limited,
      error: _error,
      until: _until,
      openDose: openDose,
    );
  }

  Future<void> _ready() async {
    if (_initialized) return;
    if (!_adapter.supported) {
      _initialized = true;
      return;
    }
    await _adapter.initialize(_onOpen);
    final reset = _sessionReset;
    try {
      final cached = await _store.read();
      _config = reset == _sessionReset ? cached : NotificationConfig();
    } catch (_) {
      await _adapter.cancelAll();
      await _store.clear();
      _config = NotificationConfig();
    }
    _initialized = true;
  }

  Future<void> _persist() async {
    final reset = _sessionReset;
    await _store.write(_config);
    if (reset != _sessionReset) await _store.clear();
  }

  Future<void> waitForIdle() async {
    while (true) {
      final pending = _tail;
      await pending;
      if (identical(pending, _tail)) return;
    }
  }

  Future<void> _serial(Future<void> Function() job) {
    final next = _tail.then((_) async {
      await _ready();
      if (_adapter.supported) await job();
    });
    _tail = next.catchError((Object _) {});
    return next;
  }

  Future<void> _guard(Future<void> Function() job) async {
    try {
      await _serial(job);
    } catch (_) {
      _error = 'Bildirimler güncellenemedi. Bağlantı ve sistem ayarlarını kontrol edip tekrar dene.';
      _publish();
    }
  }

  Future<void> refreshCapabilities() => _guard(() async {
    _caps = await _adapter.capabilities();
    _count = (await _adapter.pendingIds()).length;
    _publish();
  });
  Future<void> activate() async {
    final generation = ++_generation;
    await _guard(() async {
      final owner = await _source.owner();
      if (generation != _generation) return;
      _activeOwner = owner;
      if (_config.owner != owner) {
        await _adapter.cancelAll();
        if (generation != _generation) return;
        _config = NotificationConfig(owner: owner);
        await _persist();
      }
      await _reconcile(generation);
      _deliverTap();
    });
  }

  Future<void> sync({String? medicationChanged, String? doseChanged}) async {
    final generation = ++_generation;
    await _guard(() async {
      final stale = _config.entries
          .where(
            (e) =>
                e.medication == medicationChanged &&
                    medicationChanged != null ||
                e.dose == doseChanged && doseChanged != null,
          )
          .toList();
      for (final entry in stale) {
        await _adapter.cancel(entry.id);
      }
      if (stale.isNotEmpty) {
        _config.entries.removeWhere(stale.contains);
        await _persist();
      }
      if (generation != _generation) return;
      await _reconcile(generation);
    });
  }

  Future<void> _reconcile(int generation) async {
    if (generation != _generation) return;
    _error = null;
    _publish(busy: true);
    _caps = await _adapter.capabilities();
    if (generation != _generation) return;
    if (!_config.enabled || !_caps.allowed) {
      await _adapter.cancelAll();
      _count = 0;
      _publish();
      return;
    }
    final batch = await _source.fetch();
    if (generation != _generation) return;
    _activeOwner = batch.owner;
    if (_config.owner != batch.owner) {
      await _adapter.cancelAll();
      if (generation != _generation) return;
      _config = NotificationConfig(owner: batch.owner);
      await _persist();
      _count = 0;
      _publish();
      return;
    }
    final now = DateTime.now().toUtc();
    final reminders = batch.reminders
        .where((e) => e.at.isAfter(now))
        .take(200)
        .toList();
    final scheduled = reminders
        .map(
          (r) => ScheduledReminder(
            notificationId: reserveNotificationId(
              batch.owner,
              r.id,
              _config.ids,
            ),
            owner: batch.owner,
            reminder: r,
            redacted: _config.redacted,
            exact: _caps.exact,
          ),
        )
        .toList();
    _config.entries = scheduled
        .map(
          (s) => CachedReminder(
            id: s.notificationId,
            dose: s.reminder.id,
            medication: s.reminder.medicationId,
            at: s.reminder.at,
          ),
        )
        .toList();
    await _persist();
    if (generation != _generation) return;
    await _adapter.cancelAll();
    _count = 0;
    for (final item in scheduled) {
      if (generation != _generation) return;
      if (!item.reminder.at.isAfter(DateTime.now().toUtc())) continue;
      try {
        await _adapter.schedule(item);
      } catch (_) {
        // Exact permission can be revoked between the capability read and scheduling.
        _caps = await _adapter.capabilities();
        if (item.exact && !_caps.exact && _caps.allowed) {
          await _adapter.schedule(
            ScheduledReminder(
              notificationId: item.notificationId,
              owner: item.owner,
              reminder: item.reminder,
              redacted: item.redacted,
              exact: false,
            ),
          );
        } else {
          rethrow;
        }
      }
      if (generation != _generation) {
        await _adapter.cancel(item.notificationId);
        return;
      }
      _count++;
    }
    _until = batch.until;
    _limited = batch.limited;
    _publish();
    _deliverTap();
  }

  Future<void> enable() async {
    final reset = _sessionReset;
    final generation = ++_generation;
    await _guard(() async {
      _publish(busy: true);
      final owner = await _source.owner();
      if (generation != _generation) return;
      _activeOwner = owner;
      if (_config.owner != owner) {
        await _adapter.cancelAll();
        if (generation != _generation) return;
        _config = NotificationConfig(owner: owner);
      }
      await _adapter.requestPermission();
      _caps = await _adapter.capabilities();
      if (reset != _sessionReset) return;
      _config.enabled = _caps.allowed;
      await _persist();
      if (generation != _generation) return;
      await _reconcile(generation);
    });
  }

  Future<void> disable() async {
    ++_generation;
    await _guard(() async {
      _config.enabled = false;
      await _adapter.cancelAll();
      _config.entries = [];
      await _persist();
      _count = 0;
      _error = null;
      _publish();
    });
  }

  Future<void> setRedacted(bool value) async {
    final generation = ++_generation;
    await _guard(() async {
      _config.redacted = value;
      await _adapter.cancelAll();
      _count = 0;
      await _persist();
      await _reconcile(generation);
    });
  }

  Future<void> requestExact() async {
    await _guard(() async {
      await _adapter.requestExactPermission();
    });
    await sync();
  }

  Future<void> openSettings() => _guard(_adapter.openSettings);
  Future<void> openBatterySettings() => _guard(_adapter.openBatterySettings);
  Future<void> endSession() async {
    // Do not wait behind a fetch that may itself be waiting for auth cleanup.
    ++_generation;
    ++_sessionReset;
    _tap = null;
    _activeOwner = null;
    if (!_adapter.supported) return;
    await _adapter.cancelAll();
    await _store.clear();
    _config = NotificationConfig();
    _count = 0;
    _until = null;
    _error = null;
    _publish();
  }

  void _onOpen(String payload) {
    _tap = payload;
    _deliverTap();
  }

  void _deliverTap() {
    final payload = _tap;
    if (payload == null ||
        _activeOwner == null ||
        _config.owner != _activeOwner) {
      return;
    }
    try {
      final data = jsonDecode(payload) as Map<String, dynamic>;
      final id = data['dose'] as String;
      if (data['owner'] == _config.owner &&
          RegExp(r'^[0-9a-fA-F-]{36}$').hasMatch(id)) {
        _publish(openDose: id);
      }
    } catch (_) {
    } finally {
      _tap = null;
    }
  }

  void acknowledgeOpen() => _publish();
}
