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

void main() {
  testWidgets('guard, login validation, medication list and logout', (
    tester,
  ) async {
    final store = MemoryStore();
    var calls = 0;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          tokenStoreProvider.overrideWithValue(store),
          apiProvider.overrideWithValue(
            ApiClient(
              MockClient((r) async {
                calls++;
                if (r.url.path.endsWith('/login')) {
                  return http.Response(
                    jsonEncode({
                      'accessToken': 'fictional-access',
                      'refreshToken': 'fictional-refresh',
                    }),
                    200,
                    headers: {
                      'content-type': 'application/json; charset=utf-8',
                    },
                  );
                }
                if (r.url.path.endsWith('/logout')) {
                  return http.Response('', 204);
                }
                return http.Response(
                  jsonEncode({
                    'content': [
                      {
                        'id': 'fictional',
                        'name': 'Örnek ilaç',
                        'unit': 'tablet',
                        'initialStock': '30',
                      },
                    ],
                    'page': 0,
                    'totalPages': 1,
                  }),
                  200,
                  headers: {'content-type': 'application/json; charset=utf-8'},
                );
              }),
              Uri.parse('https://example.invalid/api/v1'),
            ),
          ),
        ],
        child: const MedTrackApp(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Giriş yap'), findsNWidgets(2));
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();
    expect(find.text('Geçerli bir e-posta girin.'), findsOneWidget);
    expect(calls, 0);
    await tester.enterText(
      find.byType(TextFormField).at(0),
      'fictional@example.invalid',
    );
    await tester.enterText(
      find.byType(TextFormField).at(1),
      'fictional-password',
    );
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();
    expect(find.text('İlaçlarım'), findsNWidgets(2));
    expect(find.text('Örnek ilaç'), findsOneWidget);
    expect(find.textContaining('30'), findsNothing);
    await tester.tap(find.byTooltip('Çıkış yap'));
    await tester.pumpAndSettle();
    expect(find.text('Giriş yap'), findsNWidgets(2));
    expect(store.tokens, isNull);
  });
  testWidgets('small screen and large text remain scrollable', (tester) async {
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [tokenStoreProvider.overrideWithValue(MemoryStore())],
        child: const MedTrackApp(),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.byType(ListView), findsOneWidget);
  });
  testWidgets('empty list, safe network error and retry', (tester) async {
    var fail = true;
    final store = MemoryStore()
      ..tokens = const Tokens('fictional-access', 'fictional-refresh');
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          tokenStoreProvider.overrideWithValue(store),
          apiProvider.overrideWithValue(
            ApiClient(
              MockClient((r) async {
                if (r.url.path.endsWith('/account/me')) {
                  return http.Response('{}', 200);
                }
                if (fail) {
                  throw http.ClientException('secret must not be rendered');
                }
                return http.Response(
                  '{"content":[],"page":0,"totalPages":0}',
                  200,
                );
              }),
              Uri.parse('https://example.invalid/api/v1'),
            ),
          ),
        ],
        child: const MedTrackApp(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('Sunucuya ulaşılamadı'), findsOneWidget);
    expect(find.textContaining('secret'), findsNothing);
    fail = false;
    await tester.tap(find.text('Tekrar dene'));
    await tester.pumpAndSettle();
    expect(find.text('Henüz aktif ilaç kaydın yok.'), findsOneWidget);
  });
  testWidgets(
    'pagination fetches the next page and preserves decimal semantics',
    (tester) async {
      final store = MemoryStore()
        ..tokens = const Tokens('fictional-access', 'fictional-refresh');
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            tokenStoreProvider.overrideWithValue(store),
            apiProvider.overrideWithValue(
              ApiClient(
                MockClient((r) async {
                  if (r.url.path.endsWith('/account/me')) {
                    return http.Response('{}', 200);
                  }
                  final page = int.parse(r.url.queryParameters['page']!);
                  return http.Response(
                    jsonEncode({
                      'content': [
                        {
                          'id': 'fixture-$page',
                          'name': 'Example $page',
                          'unit': 'tablet',
                          'initialStock': null,
                        },
                      ],
                      'page': page,
                      'totalPages': 2,
                    }),
                    200,
                  );
                }),
                Uri.parse('https://example.invalid/api/v1'),
              ),
            ),
          ],
          child: const MedTrackApp(),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Example 0'), findsOneWidget);
      await tester.tap(find.text('Sonraki'));
      await tester.pumpAndSettle();
      expect(find.text('Example 1'), findsOneWidget);
      expect(find.text('Example 0'), findsNothing);
    },
  );
}
