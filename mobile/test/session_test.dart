import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:medtrack/core/api_client.dart';
import 'package:medtrack/core/providers.dart';
import 'package:medtrack/core/token_store.dart';
import 'package:medtrack/core/session_cleanup.dart';
import 'package:medtrack/features/auth/session.dart';

class MemoryStore implements TokenStore {
  Tokens? tokens;
  bool failWrite = false;
  bool failClear = false;
  @override
  Future<Tokens?> read() async => tokens;
  @override
  Future<void> write(Tokens value) async {
    if (failWrite) throw StateError('unavailable');
    tokens = value;
  }

  @override
  Future<void> clear() async {
    if (failClear) throw StateError("unavailable");
    tokens = null;
  }
}

http.Response jsonResponse(Object data, [int status = 200]) =>
    http.Response(jsonEncode(data), status);
Future<ProviderContainer> setup(
  MemoryStore store,
  Future<http.Response> Function(http.Request) handler,
) async {
  final container = ProviderContainer(
    overrides: [
      tokenStoreProvider.overrideWithValue(store),
      apiProvider.overrideWithValue(
        ApiClient(
          MockClient(handler),
          Uri.parse('https://example.invalid/api/v1'),
        ),
      ),
    ],
  );
  container.read(sessionProvider);
  await Future<void>.delayed(Duration.zero);
  return container;
}

void main() {
  test('restores no session without an API request', () async {
    final c = await setup(
      MemoryStore(),
      (_) async => throw StateError('unexpected request'),
    );
    addTearDown(c.dispose);
    expect(c.read(sessionProvider).status, SessionStatus.signedOut);
  });
  test(
    'concurrent 401 responses rotate once and retry with new access',
    () async {
      final store = MemoryStore();
      var refreshes = 0;
      var expired = false;
      final c = await setup(store, (r) async {
        if (r.url.path.endsWith('/login')) {
          return jsonResponse({'accessToken': 'old', 'refreshToken': 'r1'});
        }
        if (r.url.path.endsWith('/refresh')) {
          refreshes++;
          await Future<void>.delayed(const Duration(milliseconds: 20));
          return jsonResponse({'accessToken': 'new', 'refreshToken': 'r2'});
        }
        if (expired && r.headers['Authorization'] == 'Bearer old') {
          return jsonResponse({'code': 'UNAUTHORIZED'}, 401);
        }
        return jsonResponse({'ok': true});
      });
      addTearDown(c.dispose);
      final controller = c.read(sessionProvider.notifier);
      await controller.login('fictional@example.invalid', 'fictional-password');
      expired = true;
      await Future.wait(
        List.generate(5, (_) => controller.authorized('GET', '/medications')),
      );
      expect(refreshes, 1);
      expect(store.tokens!.refreshToken, 'r2');
      expect(c.read(sessionProvider).status, SessionStatus.signedIn);
    },
  );
  test('invalid refresh clears secure session', () async {
    final store = MemoryStore()..tokens = const Tokens('expired', 'revoked');
    final c = await setup(
      store,
      (_) async => jsonResponse({'code': 'UNAUTHORIZED'}, 401),
    );
    addTearDown(c.dispose);
    expect(store.tokens, isNull);
    expect(c.read(sessionProvider).status, SessionStatus.signedOut);
  });
  test('offline restore preserves tokens and offers retry', () async {
    final store = MemoryStore()..tokens = const Tokens('valid', 'refresh');
    final c = await setup(
      store,
      (_) async => throw http.ClientException('offline'),
    );
    addTearDown(c.dispose);
    expect(store.tokens, isNotNull);
    expect(c.read(sessionProvider).status, SessionStatus.unavailable);
  });
  test(
    'ambiguous refresh network failure clears token instead of replay',
    () async {
      final store = MemoryStore()..tokens = const Tokens('expired', 'r1');
      var refreshes = 0;
      final c = await setup(store, (r) async {
        if (r.url.path.endsWith('/refresh')) {
          refreshes++;
          throw http.ClientException('offline');
        }
        return jsonResponse({'code': 'UNAUTHORIZED'}, 401);
      });
      addTearDown(c.dispose);
      expect(refreshes, 1);
      expect(store.tokens, isNull);
      expect(c.read(sessionProvider).status, SessionStatus.signedOut);
    },
  );
  test(
    'failed token persistence revokes issued session and does not sign in',
    () async {
      final store = MemoryStore()..failWrite = true;
      var revoked = false;
      final c = await setup(store, (r) async {
        if (r.url.path.endsWith('/logout')) {
          revoked = true;
          return http.Response('', 204);
        }
        return jsonResponse({
          'accessToken': 'access',
          'refreshToken': 'refresh',
        });
      });
      addTearDown(c.dispose);
      await expectLater(
        c
            .read(sessionProvider.notifier)
            .login('fictional@example.invalid', 'fictional-password'),
        throwsA(isA<ApiFailure>()),
      );
      expect(revoked, isTrue);
      expect(c.read(sessionProvider).status, SessionStatus.signedOut);
    },
  );
  test(
    'offline logout clears local tokens and reports unconfirmed revoke',
    () async {
      final store = MemoryStore()..tokens = const Tokens('valid', 'refresh');
      final c = await setup(store, (r) async {
        if (r.url.path.endsWith('/logout')) {
          throw http.ClientException('offline');
        }
        return jsonResponse({'id': 'fictional'});
      });
      addTearDown(c.dispose);
      await expectLater(
        c.read(sessionProvider.notifier).logout(),
        throwsA(isA<ApiFailure>()),
      );
      expect(store.tokens, isNull);
      expect(c.read(sessionProvider).status, SessionStatus.signedOut);
    },
  );
  test(
    'failed local deletion retries deletion instead of restoring old tokens',
    () async {
      final store = MemoryStore()..tokens = const Tokens('valid', 'refresh');
      final c = await setup(store, (_) async => jsonResponse({'ok': true}));
      addTearDown(c.dispose);
      store.failClear = true;
      await expectLater(
        c.read(sessionProvider.notifier).logout(),
        throwsA(isA<ApiFailure>()),
      );
      expect(c.read(sessionProvider).status, SessionStatus.unavailable);
      store.failClear = false;
      await c.read(sessionProvider.notifier).restore();
      expect(store.tokens, isNull);
      expect(c.read(sessionProvider).status, SessionStatus.signedOut);
    },
  );
  test(
    'authorized POST keeps body and idempotency key across refresh',
    () async {
      final store = MemoryStore()..tokens = const Tokens('old', 'r1');
      final requests = <http.Request>[];
      final c = await setup(store, (r) async {
        if (r.url.path.endsWith('/account/me')) return jsonResponse({});
        if (r.url.path.endsWith('/refresh')) {
          return jsonResponse({'accessToken': 'new', 'refreshToken': 'r2'});
        }
        requests.add(r);
        return r.headers['Authorization'] == 'Bearer old'
            ? jsonResponse({'code': 'UNAUTHORIZED'}, 401)
            : jsonResponse({});
      });
      addTearDown(c.dispose);
      await c
          .read(sessionProvider.notifier)
          .authorized(
            'POST',
            '/doses/fixture/actions',
            body: {'action': 'TAKEN'},
            headers: {'Idempotency-Key': 'fixture-operation'},
          );
      expect(requests, hasLength(2));
      expect(requests[0].body, requests[1].body);
      expect(requests[1].headers['Idempotency-Key'], 'fixture-operation');
    },
  );
  test('failed notification cleanup clears tokens and blocks restoration until retry', () async {
    final store = MemoryStore()..tokens = const Tokens('fictional', 'refresh');
    final c = await setup(store, (_) async => jsonResponse({}));
    addTearDown(c.dispose);
    Future<void> failure() async {
      throw StateError('OS unavailable');
    }

    c.read(sessionCleanupProvider).add(failure);
    await expectLater(
      c.read(sessionProvider.notifier).logout(),
      throwsA(isA<ApiFailure>()),
    );
    expect(store.tokens, isNull);
    expect(c.read(sessionProvider).status, SessionStatus.unavailable);
    c.read(sessionCleanupProvider).remove(failure);
    await c.read(sessionProvider.notifier).restore();
    expect(c.read(sessionProvider).status, SessionStatus.signedOut);
  });
}
