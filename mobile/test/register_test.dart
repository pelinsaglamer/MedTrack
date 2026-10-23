import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:medtrack/app.dart';
import 'package:medtrack/core/api_client.dart';
import 'package:medtrack/core/providers.dart';

import 'session_test.dart' show MemoryStore;

Future<void> openRegister(
  WidgetTester tester,
  Future<http.Response> Function(http.Request) handler,
) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        tokenStoreProvider.overrideWithValue(MemoryStore()),
        apiProvider.overrideWithValue(
          ApiClient(
            MockClient(handler),
            Uri.parse('https://example.invalid/api/v1'),
          ),
        ),
      ],
      child: const MedTrackApp(),
    ),
  );
  await tester.pumpAndSettle();
  await tester.ensureVisible(find.text('Hesabın yok mu? Kayıt ol'));
  await tester.tap(find.text('Hesabın yok mu? Kayıt ol'));
  await tester.pumpAndSettle();
}

Future<void> submit(WidgetTester tester) async {
  await tester.ensureVisible(find.text('Hesap oluştur'));
  await tester.tap(find.text('Hesap oluştur'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'signup validates password and confirmation, sends timezone and returns to login',
    (tester) async {
      Map<String, dynamic>? body;
      await openRegister(tester, (r) async {
        body = jsonDecode(r.body) as Map<String, dynamic>;
        return http.Response('{}', 201);
      });
      await submit(tester);
      expect(find.text('Geçerli bir e-posta girin.'), findsOneWidget);
      expect(body, isNull);
      await tester.enterText(
        find.byType(TextFormField).at(0),
        'fictional@example.invalid',
      );
      await tester.enterText(find.byType(TextFormField).at(1), 'short');
      await tester.enterText(find.byType(TextFormField).at(2), 'different');
      await submit(tester);
      expect(find.text('En az 12 karakterli bir şifre girin.'), findsOneWidget);
      expect(find.text('Şifreler aynı olmalı.'), findsOneWidget);
      expect(body, isNull);
      await tester.enterText(
        find.byType(TextFormField).at(1),
        'fictional-password',
      );
      await tester.enterText(
        find.byType(TextFormField).at(2),
        'fictional-password',
      );
      await tester.enterText(find.byType(TextFormField).at(3), 'Europe/Berlin');
      await submit(tester);
      expect(body, {
        'email': 'fictional@example.invalid',
        'password': 'fictional-password',
        'timezone': 'Europe/Berlin',
      });
      expect(find.text('Giriş yap'), findsNWidgets(2));
      expect(
        find.text(
          'Hesabın oluşturuldu. E-posta ve şifrenle giriş yapabilirsin.',
        ),
        findsOneWidget,
      );
    },
  );
  testWidgets('signup renders conflict safely and stays on the form', (
    tester,
  ) async {
    await openRegister(
      tester,
      (_) async => http.Response(
        '{"code":"RESOURCE_CONFLICT","message":"sensitive server details"}',
        409,
      ),
    );
    await tester.enterText(
      find.byType(TextFormField).at(0),
      'fictional@example.invalid',
    );
    await tester.enterText(
      find.byType(TextFormField).at(1),
      'fictional-password',
    );
    await tester.enterText(
      find.byType(TextFormField).at(2),
      'fictional-password',
    );
    await submit(tester);
    expect(find.textContaining('Mevcut hesabınla giriş'), findsOneWidget);
    expect(find.textContaining('sensitive'), findsNothing);
    expect(find.byType(TextFormField), findsNWidgets(4));
  });
}
