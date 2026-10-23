typedef Json = Map<String, dynamic>;

class PageData<T> {
  PageData(Json j, T Function(Json) parse)
    : items = (j['content'] as List).map((e) => parse(e as Json)).toList(),
      page = j['page'] as int,
      totalPages = j['totalPages'] as int;
  final List<T> items;
  final int page, totalPages;
}

class Dose {
  Dose(Json j)
    : id = j['id'] as String,
      medicationId = j['medicationId'] as String,
      name = j['medicationName'] as String,
      unit = j['unit'] as String,
      quantity = j['quantityPerDose'] as String,
      date = j['localDate'] as String,
      time = j['localTime'] as String,
      zone = j['timezone'] as String,
      due = DateTime.parse(j['dueAtUtc'] as String),
      status = j['status'] as String,
      version = j['version'] as int,
      lastActionId = j['lastActionId'] as String?,
      recordedAt = j['recordedAt'] as String?,
      missedAt = j['missedAt'] as String?;
  final String id, medicationId, name, unit, quantity, date, time, zone, status;
  final String? lastActionId, recordedAt, missedAt;
  final DateTime due;
  final int version;
  bool get canConfirm => status == 'DUE' || status == 'MISSED';
  bool get canUndo =>
      (status == 'TAKEN' || status == 'SKIPPED') && lastActionId != null;
}

class DailyPage extends PageData<Dose> {
  DailyPage(Json j)
    : date = j['date'] as String,
      zone = j['timezone'] as String,
      super(j, Dose.new);
  final String date, zone;
}

class SchedulePlan {
  SchedulePlan(Json j)
    : id = j['id'] as String,
      version = j['version'] as int,
      times = (j['times'] as List).cast<String>(),
      quantity = j['quantityPerDose'] as String,
      zone = j['timezone'] as String,
      start = j['startDate'] as String,
      end = j['endDate'] as String?,
      from = j['effectiveFrom'] as String,
      until = j['effectiveUntil'] as String?;
  final String id, quantity, zone, start, from;
  final String? end, until;
  final int version;
  final List<String> times;
}

class ActionEvent {
  ActionEvent(Json j)
    : action = j['action'] as String,
      status = j['status'] as String,
      recorded = j['recordedAt'] as String,
      serverRecorded = j['serverRecordedAt'] as String,
      delta = j['inventoryDelta'] as String;
  final String action, status, recorded, serverRecorded, delta;
}

class Movement {
  Movement(Json j)
    : reason = j['reason'] as String,
      quantity = j['changeQuantity'] as String,
      unit = j['unit'] as String,
      created = j['createdAt'] as String;
  final String reason, quantity, unit, created;
}

class InventoryView {
  InventoryView(Json j)
    : unit = j['unit'] as String,
      known = (j['balance'] as Json)['known'] as bool,
      quantity = (j['balance'] as Json)['quantity'] as String?,
      warnings = ((j['balance'] as Json)['warnings'] as List).cast<String>(),
      projection = Projection(j['projection'] as Json),
      movements = PageData(j['movements'] as Json, Movement.new);
  final String unit;
  final bool known;
  final String? quantity;
  final List<String> warnings;
  final Projection projection;
  final PageData<Movement> movements;
}

class Projection {
  Projection(Json j)
    : status = j['status'] as String,
      reason = j['reason'] as String?,
      days = j['fullDays'] as String?,
      date = j['estimatedDepletionDate'] as String?,
      zone = j['timezone'] as String?;
  final String status;
  final String? reason, days, date, zone;
}

class WeeklySummary {
  WeeklySummary(Json j)
    : start = j['weekStart'] as String,
      zone = j['timezone'] as String,
      total = j['scheduledDoses'] as int,
      taken = j['taken'] as int,
      skipped = j['skipped'] as int,
      missed = j['missed'] as int,
      awaiting = j['awaitingConfirmation'] as int,
      pending = j['pending'] as int,
      late = j['unconfirmedAtDeadline'] as int;
  final String start, zone;
  final int total, taken, skipped, missed, awaiting, pending, late;
}

String statusLabel(String value) => switch (value) {
  'PENDING' => 'Planlandı',
  'DUE' => 'Teyit bekliyor',
  'MISSED' => 'Zamanında teyit yok',
  'TAKEN' => 'Alındı',
  'SKIPPED' => 'Atlandı',
  'UNDO' => 'Geri alındı',
  _ => value,
};
String movementLabel(String value) => switch (value) {
  'OPENING' => 'Başlangıç stoku',
  'RESTOCK' => 'Stok eklendi',
  'CORRECTION' => 'Stok düzeltildi',
  'CONSUMPTION' => 'Doz tüketimi',
  'UNDO' => 'Geri alma telafisi',
  _ => value,
};
String displayDate(String iso) {
  final p = iso.split('-');
  return p.length == 3 ? '${p[2]}.${p[1]}.${p[0]}' : iso;
}

String displayInstant(String iso) =>
    '${iso.replaceFirst('T', ' ').substring(0, 16)} UTC';
String normalizeDecimal(String value) => value.trim().replaceAll(',', '.');
String? decimalError(
  String? value, {
  bool positive = false,
  bool optional = false,
}) {
  final text = normalizeDecimal(value ?? '');
  if (optional && text.isEmpty) return null;
  if (!RegExp(r'^\d{1,13}(\.\d{1,6})?$').hasMatch(text)) {
    return 'En fazla 13 tam ve 6 ondalık basamak girin.';
  }
  if (positive && !RegExp('[1-9]').hasMatch(text)) {
    return 'Sıfırdan büyük bir miktar girin.';
  }
  return null;
}

DateTime calendarDate(String value) => DateTime.parse('${value}T00:00:00Z');
