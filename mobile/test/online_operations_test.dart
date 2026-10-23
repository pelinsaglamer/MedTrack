import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:medtrack/core/api_client.dart';
import 'package:medtrack/core/providers.dart';
import 'package:medtrack/core/token_store.dart';
import 'package:medtrack/features/online/data.dart';
import 'package:medtrack/features/auth/session.dart';
import 'package:medtrack/features/online/models.dart';

import 'session_test.dart' show MemoryStore;

Future<ProviderContainer> make(
  Future<http.Response> Function(http.Request) handler,
) async {
  final c = ProviderContainer(
    overrides: [
      tokenStoreProvider.overrideWithValue(
        MemoryStore()
          ..tokens = const Tokens('fictional-access', 'fictional-refresh'),
      ),
      apiProvider.overrideWithValue(
        ApiClient(
          MockClient(
            (r) async => r.url.path.endsWith('/account/me')
                ? http.Response('{}', 200)
                : handler(r),
          ),
          Uri.parse('https://example.invalid/api/v1'),
        ),
      ),
    ],
  );
  c.read(onlineApiProvider);
  await Future<void>.delayed(Duration.zero);
  return c;
}

void main() {
  test(
    'ambiguous action retry retains exact payload, operation ID and recordedAt',
    () async {
      final requests = <http.Request>[];
      final c = await make((r) async {
        requests.add(r);
        if (requests.length == 1) throw http.ClientException('timeout');
        return http.Response('{}', 201);
      });
      addTearDown(c.dispose);
      final ops = c.read(operationsProvider.notifier);
      final first = ops.begin('dose/fixture', '/doses/fixture/actions', {
        'action': 'TAKEN',
        'recordedAt': '2026-10-23T00:00:00Z',
        'expectedVersion': 0,
      });
      await expectLater(ops.send('dose/fixture'), throwsA(isA<ApiFailure>()));
      expect(c.read(operationsProvider)['dose/fixture'], same(first));
      expect(
        ops.begin('dose/fixture', '/ignored', {'action': 'SKIPPED'}),
        same(first),
      );
      await ops.send('dose/fixture');
      expect(requests[0].body, requests[1].body);
      expect(
        requests[0].headers['Idempotency-Key'],
        requests[1].headers['Idempotency-Key'],
      );
      expect(jsonDecode(requests[0].body)['clientOperationId'], first.key);
      expect(c.read(operationsProvider), isEmpty);
    },
  );
  test('double send while in flight makes one request', () async {
    final complete = Completer<http.Response>();
    var calls = 0;
    final c = await make((r) {
      calls++;
      return complete.future;
    });
    addTearDown(c.dispose);
    final ops = c.read(operationsProvider.notifier);
    ops.begin('stock', '/inventory/fixture/adjustments', {
      'reason': 'RESTOCK',
      'quantity': '0.123456',
    });
    final pending = ops.send('stock');
    await Future<void>.delayed(Duration.zero);
    expect(await ops.send('stock'), isNull);
    expect(calls, 1);
    complete.complete(http.Response('{}', 201));
    await pending;
  });
  test('conflict clears pending operation so state must be refreshed before new choice', () async {
    final c = await make(
      (r) async => http.Response('{"code":"DOSE_STATE_CONFLICT"}', 409),
    );
    addTearDown(c.dispose);
    final ops = c.read(operationsProvider.notifier);
    ops.begin('dose', '/doses/fixture/actions', {'action': 'UNDO'});
    await expectLater(ops.send('dose'), throwsA(isA<ApiFailure>()));
    expect(c.read(operationsProvider), isEmpty);
  });
  test(
    'server failure retains pending stock correction for exact replay',
    () async {
      final c = await make(
        (r) async => http.Response('{"code":"INTERNAL_ERROR"}', 500),
      );
      addTearDown(c.dispose);
      final ops = c.read(operationsProvider.notifier);
      final op = ops.begin('stock', '/inventory/fixture/adjustments', {
        'reason': 'CORRECTION',
        'quantity': '0',
      });
      await expectLater(ops.send('stock'), throwsA(isA<ApiFailure>()));
      expect(c.read(operationsProvider)['stock'], same(op));
    },
  );
  test('decimal validation preserves exact strings and distinguishes zero', () {
    expect(normalizeDecimal(' 0,123456 '), '0.123456');
    expect(decimalError('0'), isNull);
    expect(decimalError('0', positive: true), isNotNull);
    expect(decimalError('-1'), isNotNull);
    expect(decimalError('1.1234567'), isNotNull);
    expect(decimalError('12345678901234'), isNotNull);
    expect(decimalError('', optional: true), isNull);
  });
  test('calendar date navigation is UTC and independent of device DST', () {
    final date = calendarDate('2026-10-25');
    expect(date.isUtc, isTrue);
    expect(
      date.add(const Duration(days: 1)).toIso8601String().substring(0, 10),
      '2026-10-26',
    );
  });
  test(
    'logout clears pending operations before another account can use the app',
    () async {
      final c = await make((r) async => http.Response('', 204));
      addTearDown(c.dispose);
      c.read(operationsProvider.notifier).begin(
        'dose',
        '/doses/fixture/actions',
        {'action': 'TAKEN'},
      );
      expect(c.read(operationsProvider), isNotEmpty);
      await c.read(sessionProvider.notifier).logout();
      expect(c.read(operationsProvider), isEmpty);
    },
  );
}
