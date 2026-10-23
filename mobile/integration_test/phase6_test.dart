import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:http/http.dart' as http;
import 'package:medtrack/app.dart';
import 'package:medtrack/core/api_client.dart';
import 'package:medtrack/core/token_store.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

Future<void> waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 30));
  while (finder.evaluate().isEmpty && DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(milliseconds: 200));
  }
  expect(finder, findsWidgets);
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'real backend login, secure-store restore, medication list, logout',
    (tester) async {
      // Creates fictional fixtures only. No real account or credentials are used.
      const base = String.fromEnvironment(
        'API_BASE_URL',
        defaultValue: 'http://10.0.2.2:8080/api/v1',
      );
      final client = http.Client();
      addTearDown(client.close);
      final api = ApiClient(client, Uri.parse(base));
      final unique = '${DateTime.now().microsecondsSinceEpoch}';
      final email = 'phase6-$unique@example.invalid';
      final random = Random.secure();
      final password = List.generate(
        32,
        (_) => random.nextInt(16).toRadixString(16),
      ).join();
      await api.request(
        'POST',
        '/auth/register',
        body: {
          'email': email,
          'password': password,
          'timezone': 'Europe/Istanbul',
        },
      );
      final fixtureTokens = Tokens.fromJson(
        await api.request(
          'POST',
          '/auth/login',
          body: {'email': email, 'password': password},
        ),
      );
      await api.request(
        'POST',
        '/medications',
        token: fixtureTokens.accessToken,
        body: {
          'name': 'Örnek ilaç — Phase 6',
          'unit': 'tablet',
          'initialStock': '30',
        },
      );
      await api.request(
        'POST',
        '/auth/logout',
        body: {'refreshToken': fixtureTokens.refreshToken},
      );
      final store = SecureTokenStore(const FlutterSecureStorage());
      await store.clear();
      await tester.pumpWidget(const ProviderScope(child: MedTrackApp()));
      await waitFor(tester, find.byType(TextFormField));
      await tester.enterText(find.byType(TextFormField).at(0), email);
      await tester.enterText(find.byType(TextFormField).at(1), password);
      await tester.tap(find.byType(FilledButton));
      await waitFor(tester, find.text('Örnek ilaç — Phase 6'));
      expect(find.text('İlaçlarım'), findsOneWidget);
      expect(find.text('Örnek ilaç — Phase 6'), findsOneWidget);
      expect(await store.read(), isNotNull);
      // Rebuild with a fresh ProviderScope: restoration must use Android secure storage.
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();
      await tester.pumpWidget(
        ProviderScope(key: UniqueKey(), child: const MedTrackApp()),
      );
      await waitFor(tester, find.text('Örnek ilaç — Phase 6'));
      expect(find.text('Örnek ilaç — Phase 6'), findsOneWidget);
      final beforeLogout = await store.read();
      await tester.tap(find.byTooltip('Çıkış yap'));
      await waitFor(tester, find.byType(TextFormField));
      expect(find.text('Giriş yap'), findsNWidgets(2));
      expect(await store.read(), isNull);
      await expectLater(
        api.request('GET', '/account/me', token: beforeLogout!.accessToken),
        throwsA(isA<ApiFailure>().having((e) => e.status, 'status', 401)),
      );
    },
  );
}
