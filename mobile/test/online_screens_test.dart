import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:medtrack/app.dart';
import 'package:medtrack/core/api_client.dart';
import 'package:medtrack/core/providers.dart';
import 'package:medtrack/core/token_store.dart';

import 'session_test.dart' show MemoryStore;

final med = {
  'id': 'fixture',
  'name': 'Example',
  'unit': 'tablet',
  'initialStock': null,
  'archivedAt': null,
};
Map<String, dynamic> page(List<dynamic> items) => {
  'content': items,
  'page': 0,
  'totalPages': items.isEmpty ? 0 : 1,
};
http.Response jsonResponse(Object data, [int status = 200]) => http.Response(
  jsonEncode(data),
  status,
  headers: {'content-type': 'application/json; charset=utf-8'},
);
Future<void> open(
  WidgetTester tester,
  String path,
  Future<http.Response> Function(http.Request) handler,
) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        tokenStoreProvider.overrideWithValue(
          MemoryStore()
            ..tokens = const Tokens('fictional-access', 'fictional-refresh'),
        ),
        apiProvider.overrideWithValue(
          ApiClient(
            MockClient((r) async {
              if (r.url.path.endsWith('/account/me')) {
                return jsonResponse({'timezone': 'Europe/Istanbul'});
              }
              if (r.url.path == '/api/v1/medications') {
                return r.method == 'GET' ? jsonResponse(page([])) : handler(r);
              }
              if (r.url.path == '/api/v1/medications/fixture') {
                return jsonResponse(med);
              }
              return handler(r);
            }),
            Uri.parse('https://example.invalid/api/v1'),
          ),
        ),
      ],
      child: const MedTrackApp(),
    ),
  );
  await tester.pumpAndSettle();
  final c = ProviderScope.containerOf(tester.element(find.byType(MedTrackApp)));
  c.read(routerProvider).go(path);
  await tester.pumpAndSettle();
}

Future<void> tapText(WidgetTester tester, String text) async {
  await tester.ensureVisible(find.text(text).last);
  await tester.tap(find.text(text).last);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'medication form validates decimal input and blocks duplicate creation after timeout',
    (tester) async {
      var calls = 0;
      await open(tester, '/medications/new', (r) async {
        calls++;
        throw http.ClientException('uncertain');
      });
      await tapText(tester, 'Kaydet');
      expect(calls, 0);
      await tester.enterText(find.byType(TextFormField).at(0), 'Example');
      await tester.enterText(find.byType(TextFormField).at(1), 'tablet');
      await tester.enterText(find.byType(TextFormField).at(2), '1.1234567');
      await tapText(tester, 'Kaydet');
      expect(calls, 0);
      await tester.enterText(find.byType(TextFormField).at(2), '1,5');
      await tapText(tester, 'Kaydet');
      expect(calls, 1);
      expect(
        find.textContaining('Kayıt sunucuya ulaşmış olabilir'),
        findsOneWidget,
      );
      expect(
        tester.widget<FilledButton>(find.byType(FilledButton).last).onPressed,
        isNull,
      );
    },
  );
  testWidgets(
    'unknown inventory never renders a numeric zero or numeric forecast',
    (tester) async {
      await open(
        tester,
        '/medications/fixture/inventory',
        (r) async => jsonResponse({
          'unit': 'tablet',
          'balance': {
            'known': false,
            'quantity': null,
            'warnings': ['STOCK_UNKNOWN'],
          },
          'projection': {'status': 'UNKNOWN', 'reason': 'UNKNOWN_STOCK'},
          'movements': page([]),
        }),
      );
      expect(find.text('Güncel stok bilinmiyor'), findsOneWidget);
      expect(find.textContaining('Tahmin üretilemedi.'), findsOneWidget);
      expect(find.textContaining('0 tablet'), findsNothing);
      await tester.scrollUntilVisible(
        find.text('Henüz stok hareketi yok.'),
        300,
        scrollable: find
            .descendant(
              of: find.byType(ListView).last,
              matching: find.byType(Scrollable),
            )
            .first,
      );
      expect(find.text('Henüz stok hareketi yok.'), findsOneWidget);
    },
  );
  testWidgets(
    'dose confirmation cancel sends nothing; ambiguous retry uses original request',
    (tester) async {
      final requests = <http.Request>[];
      var taken = false;
      final dose = <String, dynamic>{
        'id': 'fixture',
        'medicationId': 'fixture',
        'medicationName': 'Example',
        'unit': 'tablet',
        'quantityPerDose': '1',
        'localDate': '2026-10-23',
        'localTime': '09:00',
        'timezone': 'Europe/Istanbul',
        'dueAtUtc': '2026-10-23T06:00:00Z',
        'status': 'DUE',
        'version': 0,
        'lastActionId': null,
        'recordedAt': null,
        'missedAt': null,
      };
      await open(tester, '/today/doses/fixture', (r) async {
        if (r.url.path == '/api/v1/doses') {
          return jsonResponse({
            ...page([dose]),
            'date': '2026-10-23',
            'timezone': 'Europe/Istanbul',
          });
        }
        if (r.url.path.endsWith('/actions')) {
          if (r.method == 'GET') return jsonResponse(page([]));
          requests.add(r);
          if (requests.length == 1) throw http.ClientException('uncertain');
          taken = true;
          return jsonResponse({
            'balance': {'warnings': []},
          }, 201);
        }
        return jsonResponse({
          ...dose,
          if (taken) 'status': 'TAKEN',
          if (taken) 'lastActionId': 'fixture-action',
          if (taken) 'version': 1,
        });
      });
      await tapText(tester, 'Aldım');
      await tapText(tester, 'Vazgeç');
      expect(requests, isEmpty);
      await tapText(tester, 'Aldım');
      await tapText(tester, 'Onayla');
      expect(requests, hasLength(1));
      await tapText(tester, 'Aynı işlemi tekrar dene');
      expect(requests, hasLength(2));
      expect(requests[0].body, requests[1].body);
      expect(
        requests[0].headers['Idempotency-Key'],
        requests[1].headers['Idempotency-Key'],
      );
      expect(find.text('Son seçimi geri al'), findsOneWidget);
    },
  );
  testWidgets('stock correction confirms an exact absolute decimal amount', (
    tester,
  ) async {
    await open(
      tester,
      '/medications/fixture/inventory',
      (r) async => jsonResponse({
        'unit': 'tablet',
        'balance': {'known': true, 'quantity': '15', 'warnings': []},
        'projection': {'status': 'UNKNOWN', 'reason': 'NO_FUTURE_SCHEDULE'},
        'movements': page([]),
      }),
    );
    await tapText(tester, 'Stok ekle');
    await tapText(tester, 'Güncel toplamı düzelt');
    await tester.enterText(find.byType(TextFormField).last, '17,5');
    await tapText(tester, 'Stok işlemini kaydet');
    expect(find.text('Onayla'), findsOneWidget);
    expect(
      find.textContaining('17.5 tablet olarak ayarlanacak'),
      findsOneWidget,
    );
  });
}
