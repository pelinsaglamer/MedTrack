import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:http/http.dart' as http;
import 'package:medtrack/app.dart';
import 'package:medtrack/core/api_client.dart';
import 'package:medtrack/core/token_store.dart';
import 'package:medtrack/features/online/data.dart';

import 'phase6_test.dart' show waitFor;

Future<void> tapText(WidgetTester tester, String text) async {
  // Settle keyboard resizing before scrolling a lazily built form button.
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  final finder = find.text(text).last;
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'online medication, plan, TAKEN UNDO SKIPPED, inventory and archive',
    (tester) async {
      final client = http.Client();
      addTearDown(client.close);
      final api = ApiClient(
        client,
        Uri.parse(
          const String.fromEnvironment(
            'API_BASE_URL',
            defaultValue: 'http://10.0.2.2:8080/api/v1',
          ),
        ),
      );
      final suffix = DateTime.now().microsecondsSinceEpoch;
      final email = 'phase7-$suffix@example.invalid';
      final password = List.generate(
        32,
        (_) => Random.secure().nextInt(16).toRadixString(16),
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
      final tokens = Tokens.fromJson(
        await api.request(
          'POST',
          '/auth/login',
          body: {'email': email, 'password': password},
        ),
      );
      final store = SecureTokenStore(const FlutterSecureStorage());
      await store.write(tokens);
      await tester.pumpWidget(const ProviderScope(child: MedTrackApp()));
      await waitFor(tester, find.text('İlaç ekle'));
      await tapText(tester, 'İlaç ekle');
      await tester.enterText(
        find.byType(TextFormField).at(0),
        'Example Phase 7 $suffix',
      );
      await tester.enterText(find.byType(TextFormField).at(1), 'tablet');
      await tester.enterText(find.byType(TextFormField).at(2), '10');
      await tapText(tester, 'Kaydet');
      await waitFor(tester, find.text('Kullanım planı ekle / değiştir'));
      final medications = await api.request(
        'GET',
        '/medications',
        token: tokens.accessToken,
      );
      final med =
          (medications['content'] as List).first as Map<String, dynamic>;
      final id = med['id'] as String;
      Future<String> balance() async =>
          ((await api.request(
                    'GET',
                    '/inventory/$id',
                    token: tokens.accessToken,
                  ))['balance']
                  as Map)['quantity']
              as String;
      await tapText(tester, 'Kullanım planı ekle / değiştir');
      await waitFor(tester, find.byType(TextFormField));
      await tester.enterText(find.byType(TextFormField).at(0), '00:00, 23:59');
      await tester.enterText(find.byType(TextFormField).at(1), '1,5');
      await tapText(tester, 'Planı kaydet');
      await tapText(tester, 'Onayla');
      await waitFor(tester, find.text('Sürüm 1 • 1.5 tablet / doz'));
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      await tapText(tester, 'Günlük');
      await waitFor(tester, find.text('Example Phase 7 $suffix'));
      await tester.tap(find.text('Example Phase 7 $suffix').first);
      await tester.pumpAndSettle();
      await waitFor(tester, find.text('Aldım'));
      await tapText(tester, 'Aldım');
      await tapText(tester, 'Onayla');
      await waitFor(tester, find.text('Son seçimi geri al'));
      expect(await balance(), '8.5');
      await tapText(tester, 'Son seçimi geri al');
      await tapText(tester, 'Onayla');
      await waitFor(tester, find.text('Aldım'));
      expect(await balance(), '10');
      await tapText(tester, 'Atladım');
      await tapText(tester, 'Onayla');
      await waitFor(tester, find.text('Son seçimi geri al'));
      expect(await balance(), '10');
      await tapText(tester, 'İlacın stok ve hareketleri');
      await waitFor(tester, find.text('Stok işlemini kaydet'));
      await tester.enterText(find.byType(TextFormField).last, '5');
      await tapText(tester, 'Stok işlemini kaydet');
      await tapText(tester, 'Onayla');
      await waitFor(tester, find.text('Güncel stok: 15 tablet'));
      expect(await balance(), '15');
      final container = ProviderScope.containerOf(
        tester.element(find.byType(MedTrackApp)),
      );
      expect(container.read(operationsProvider), isEmpty);
      await tapText(tester, 'Stok ekle');
      await tapText(tester, 'Güncel toplamı düzelt');
      await tester.ensureVisible(find.byType(TextFormField).last);
      await tester.tap(find.byType(TextFormField).last);
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextFormField).last, '17,5');
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<TextFormField>(find.byType(TextFormField).last)
            .controller!
            .text,
        '17,5',
      );
      expect(
        tester.state<FormState>(find.byType(Form).last).validate(),
        isTrue,
      );
      await tapText(tester, 'Stok işlemini kaydet');
      await waitFor(tester, find.text('Onayla'));
      await tapText(tester, 'Onayla');
      await waitFor(tester, find.text('Güncel stok: 17.5 tablet'));
      expect(await balance(), '17.5');

      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      // Return through the preserved dose route to the daily dashboard.
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      await tapText(tester, 'İlaçlarım');
      await waitFor(tester, find.text('Example Phase 7 $suffix'));
      await tester.tap(find.text('Example Phase 7 $suffix'));
      await tester.pumpAndSettle();
      await waitFor(tester, find.text('Düzenle'));
      await tapText(tester, 'Düzenle');
      await waitFor(tester, find.byType(TextFormField));
      expect(
        tester.widget<TextFormField>(find.byType(TextFormField).at(1)).enabled,
        isFalse,
      );
      await tester.enterText(
        find.byType(TextFormField).at(0),
        'Edited Example $suffix',
      );
      await tapText(tester, 'Kaydet');
      await waitFor(tester, find.text('Edited Example $suffix'));
      await tapText(tester, 'İlacı arşivle');
      await tapText(tester, 'Onayla');
      await waitFor(tester, find.text('Henüz aktif ilaç kaydın yok.'));
      final archived = await api.request(
        'GET',
        '/medications/$id',
        token: tokens.accessToken,
      );
      expect(archived['archivedAt'], isNotNull);
      final history = await api.request(
        'GET',
        '/doses',
        token: tokens.accessToken,
      );
      expect(
        (history['content'] as List).where((e) => e['status'] == 'SKIPPED'),
        isNotEmpty,
      );
      await tester.tap(find.byTooltip('Çıkış yap'));
      await waitFor(tester, find.byType(TextFormField));
      expect(await store.read(), isNull);
    },
  );
}
