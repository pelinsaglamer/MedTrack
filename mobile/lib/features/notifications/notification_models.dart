import 'dart:convert';

import '../online/models.dart';

class NotificationCapabilities {
  const NotificationCapabilities({
    required this.allowed,
    required this.exact,
    required this.batteryRestricted,
  });
  final bool allowed, exact, batteryRestricted;
}

class Reminder {
  Reminder(Dose dose)
    : id = dose.id,
      medicationId = dose.medicationId,
      name = dose.name,
      zone = dose.zone,
      at = dose.snoozedUntil == null
          ? dose.due
          : DateTime.parse(dose.snoozedUntil!);
  const Reminder.fixture({
    required this.id,
    required this.medicationId,
    required this.name,
    required this.zone,
    required this.at,
  });
  final String id, medicationId, name, zone;
  final DateTime at;
}

class ScheduledReminder {
  const ScheduledReminder({
    required this.notificationId,
    required this.owner,
    required this.reminder,
    required this.redacted,
    required this.exact,
  });
  final int notificationId;
  final String owner;
  final Reminder reminder;
  final bool redacted, exact;
  String get title => redacted ? 'MedTrack hatırlatması' : reminder.name;
  String get body =>
      'İlaç hatırlatıcınız var. Kaydınızı uygulamada kontrol edin.';
  String get payload => jsonEncode({'owner': owner, 'dose': reminder.id});
}

class CachedReminder {
  CachedReminder({
    required this.id,
    required this.dose,
    required this.medication,
    required this.at,
  });
  factory CachedReminder.fromJson(Json j) => CachedReminder(
    id: j['id'] as int,
    dose: j['dose'] as String,
    medication: j['medication'] as String,
    at: DateTime.parse(j['at'] as String),
  );
  final int id;
  final String dose, medication;
  final DateTime at;
  Json toJson() => {
    'id': id,
    'dose': dose,
    'medication': medication,
    'at': at.toUtc().toIso8601String(),
  };
}

class NotificationConfig {
  NotificationConfig({
    this.owner,
    this.enabled = false,
    this.redacted = true,
    Map<String, int>? ids,
    List<CachedReminder>? entries,
  }) : ids = ids ?? {},
       entries = entries ?? [];
  factory NotificationConfig.fromJson(Json j) => NotificationConfig(
    owner: j['owner'] as String?,
    enabled: j['enabled'] as bool,
    redacted: j['redacted'] as bool,
    ids: (j['ids'] as Json).map((k, v) => MapEntry(k, v as int)),
    entries: (j['entries'] as List)
        .map((e) => CachedReminder.fromJson(e as Json))
        .toList(),
  );
  String? owner;
  bool enabled, redacted;
  final Map<String, int> ids;
  List<CachedReminder> entries;
  Json toJson() => {
    'owner': owner,
    'enabled': enabled,
    'redacted': redacted,
    'ids': ids,
    'entries': entries.map((e) => e.toJson()).toList(),
  };
}

int stableNotificationHash(String value) {
  var result = 2166136261;
  for (final byte in utf8.encode(value)) {
    result = ((result ^ byte) * 16777619) & 0x7fffffff;
  }
  return result == 0 ? 1 : result;
}

int reserveNotificationId(
  String owner,
  String dose,
  Map<String, int> registry, {
  int Function(String)? hash,
}) {
  final key = '$owner/$dose';
  final previous = registry[key];
  if (previous != null) return previous;
  var id = (hash ?? stableNotificationHash)(key) & 0x7fffffff;
  if (id == 0) id = 1;
  final used = registry.values.toSet();
  while (used.contains(id)) {
    id = id == 0x7fffffff ? 1 : id + 1;
  }
  registry[key] = id;
  return id;
}
