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
import 'package:medtrack/features/notifications/android_notification_adapter.dart';
import 'package:medtrack/features/notifications/notification_controller.dart';
import 'package:medtrack/features/notifications/notification_models.dart';
import 'package:medtrack/features/notifications/notification_ports.dart';

import 'phase6_test.dart' show waitFor;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'Android local notification permissions scheduling privacy snooze and cleanup',
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
      final email = 'phase8-$suffix@example.invalid';
      final random = Random.secure();
      final password = List.generate(
        32,
        (_) => random.nextInt(16).toRadixString(16),
      ).join();
      final account = await api.request(
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
      final med = await api.request(
        'POST',
        '/medications',
        token: tokens.accessToken,
        body: {
          'name': 'Fictional notification medicine',
          'unit': 'tablet',
          'initialStock': '10',
        },
      );
      final today = await api.request(
        'GET',
        '/doses',
        token: tokens.accessToken,
      );
      final plan = {
        'times': ['00:00', '23:59'],
        'quantityPerDose': '1',
        'timezone': 'Europe/Istanbul',
        'startDate': today['date'],
      };
      await api.request(
        'POST',
        '/medications/${med['id']}/schedules',
        token: tokens.accessToken,
        body: plan,
      );
      final doses = await api.request(
        'GET',
        '/doses',
        token: tokens.accessToken,
      );
      final dose = (doses['content'] as List).first as Map<String, dynamic>;
      final secure = SecureTokenStore(const FlutterSecureStorage());
      await secure.write(tokens);
      await SecureNotificationStore(const FlutterSecureStorage()).clear();
      await tester.pumpWidget(const ProviderScope(child: MedTrackApp()));
      await waitFor(tester, find.text('İlaç ekle'));
      final container = ProviderScope.containerOf(
        tester.element(find.byType(MedTrackApp)),
      );
      final controller = container.read(
        notificationControllerProvider.notifier,
      );
      final adapter = container.read(
        notificationAdapterProvider,
      ) as AndroidNotificationAdapter;
      await controller.activate();
      const mode = String.fromEnvironment(
        'NOTIFICATION_TEST_MODE',
        defaultValue: 'granted',
      );
      if (mode == 'denied') {
        expect((await adapter.capabilities()).allowed, isFalse);
        expect(await adapter.pendingIds(), isEmpty);
        await tester.tap(find.byTooltip('Hatırlatma ayarları'));
        await tester.pumpAndSettle();
        expect(find.textContaining('Bildirim izni kapalı'), findsOneWidget);
        await tester.binding.handlePopRoute();
        await tester.pumpAndSettle();
        expect(find.text('İlaç ekle'), findsOneWidget);
        await controller.endSession();
        return;
      }
      expect((await adapter.capabilities()).allowed, isTrue);
      await controller.enable();
      final first = await adapter.plugin.pendingNotificationRequests();
      expect(first, isNotEmpty);
      expect(first.any((n) => (n.title ?? '').contains('Fictional')), isFalse);
      final ids = first.map((e) => e.id).toSet();
      await controller.sync();
      expect((await adapter.pendingIds()).toSet(), ids);
      if (mode == 'inexact') {
        expect((await adapter.capabilities()).exact, isFalse);
        expect(container.read(notificationControllerProvider).exact, isFalse);
        await controller.endSession();
        expect(await adapter.pendingIds(), isEmpty);
        return;
      }
      await api.request(
        'POST',
        '/medications/${med['id']}/schedules',
        token: tokens.accessToken,
        body: {
          ...plan,
          'times': ['00:00', '23:58'],
        },
      );
      await controller.sync(medicationChanged: med['id'] as String);
      expect((await adapter.pendingIds()).toSet().intersection(ids), isEmpty);
      expect((await adapter.capabilities()).exact, isTrue);
      await controller.setRedacted(false);
      expect(
        (await adapter.plugin.pendingNotificationRequests()).any(
          (n) => (n.title ?? '').contains('Fictional'),
        ),
        isTrue,
      );
      await controller.setRedacted(true);
      // A short, fictional alarm observes actual OS delivery; no production test button is added.
      const testId = 1900000001;
      await adapter.schedule(
        ScheduledReminder(
          notificationId: testId,
          owner: account['id'] as String,
          reminder: Reminder.fixture(
            id: dose['id'] as String,
            medicationId: med['id'] as String,
            name: 'Fictional notification medicine',
            zone: 'Europe/Istanbul',
            at: DateTime.now().toUtc().add(const Duration(seconds: 8)),
          ),
          redacted: true,
          exact: true,
        ),
      );
      final deadline = DateTime.now().add(const Duration(seconds: 35));
      while (DateTime.now().isBefore(deadline) &&
          !(await adapter.plugin.getActiveNotifications()).any(
            (n) => n.id == testId,
          )) {
        await tester.pump(const Duration(milliseconds: 500));
      }
      final active = (await adapter.plugin.getActiveNotifications()).firstWhere(
        (n) => n.id == testId,
      );
      expect(active.title, 'MedTrack hatırlatması');
      expect(active.body, isNot(contains('Fictional')));
      if (mode == 'tap' || mode == 'persistTap') {
        // The external emulator driver taps this real OS notification.
        debugPrint('PHASE8_NOTIFICATION_READY_FOR_TAP');
        await waitFor(tester, find.text('Doz detayı'));
        await waitFor(tester, find.text('Aldım'));
      } else {
        container.read(routerProvider).go('/today/doses/${dose['id']}');
        await waitFor(tester, find.text('Hatırlatmayı ertele'));
      }
      final audit = await api.request(
        'GET',
        '/doses/${dose['id']}/actions',
        token: tokens.accessToken,
      );
      expect((audit['content'] as List), isEmpty);
      await tester.ensureVisible(find.text('Hatırlatmayı ertele'));
      await tester.tap(find.text('Hatırlatmayı ertele'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('5 dakika'));
      await waitFor(
        tester,
        find.text('Hatırlatma ertelendi. Plan, doz teyidi ve stok değişmedi.'),
      );
      await controller.sync(doseChanged: dose['id'] as String);
      final snoozed = await api.request(
        'GET',
        '/doses/${dose['id']}',
        token: tokens.accessToken,
      );
      expect(snoozed['snoozedUntil'], isNotNull);
      final stock = await api.request(
        'GET',
        '/inventory/${med['id']}',
        token: tokens.accessToken,
      );
      expect((stock['balance'] as Map)['quantity'], '10');
      expect(
        (await adapter.plugin.pendingNotificationRequests()).any(
          (n) => (n.payload ?? '').contains(dose['id'] as String),
        ),
        isTrue,
      );
      if (mode == 'persist' || mode == 'persistTap') {
        await controller.waitForIdle();
        return;
      }
      // Archive must cancel the device's old slots even if a later fetch fails.
      await api.request(
        'PATCH',
        '/medications/${med['id']}',
        token: tokens.accessToken,
        body: {'archived': true},
      );
      await controller.sync(medicationChanged: med['id'] as String);
      expect(await adapter.pendingIds(), isEmpty);
      container.read(routerProvider).go('/medications');
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Çıkış yap'));
      await waitFor(tester, find.byType(TextFormField));
      expect(await adapter.pendingIds(), isEmpty);
      expect(await secure.read(), isNull);
    },
  );
}
