import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../auth/session.dart';

class Medication {
  const Medication({
    required this.id,
    required this.name,
    required this.unit,
    required this.initialStock,
    this.archivedAt,
    this.strength,
  });
  factory Medication.fromJson(Map<String, dynamic> json) => Medication(
    id: json['id'] as String,
    name: json['name'] as String,
    unit: json['unit'] as String,
    initialStock: json['initialStock'] as String?,
    archivedAt: json['archivedAt'] as String?,
    strength: json['strength'] as String?,
  );
  final String id;
  final String name;
  final String unit;
  // Exact decimal text. Initial stock is never presented as the current balance.
  final String? initialStock;
  final String? archivedAt;
  final String? strength;
}

class MedicationPage {
  MedicationPage.fromJson(Map<String, dynamic> json)
    : items = (json['content'] as List<dynamic>)
          .map((e) => Medication.fromJson(e as Map<String, dynamic>))
          .toList(),
      page = json['page'] as int,
      totalPages = json['totalPages'] as int;
  final List<Medication> items;
  final int page;
  final int totalPages;
}

final medicationsProvider = FutureProvider.autoDispose
    .family<MedicationPage, int>((ref, page) async {
      final session = ref.watch(sessionProvider);
      if (session.status != SessionStatus.signedIn) {
        throw StateError('Session required');
      }
      final data = await ref
          .read(sessionProvider.notifier)
          .authorized(
            'GET',
            '/medications?page=$page&size=20&includeArchived=false',
          );
      return MedicationPage.fromJson(data);
    });
