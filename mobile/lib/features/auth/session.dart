import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/providers.dart';
import '../../core/token_store.dart';
import 'auth_api.dart';

enum SessionStatus { loading, signedOut, signedIn, unavailable }

class SessionState {
  const SessionState(this.status, {this.error, this.epoch = 0});
  final SessionStatus status;
  final ApiFailure? error;
  final int epoch;
}

final sessionProvider = NotifierProvider<SessionController, SessionState>(
  SessionController.new,
);

class SessionController extends Notifier<SessionState> {
  Tokens? _tokens;
  Future<void>? _refreshing;
  int _epoch = 0;
  bool _clearRequired = false;
  ApiClient get _api => ref.read(apiProvider);
  AuthApi get _auth => AuthApi(_api);
  TokenStore get _store => ref.read(tokenStoreProvider);
  @override
  SessionState build() {
    Future.microtask(restore);
    return const SessionState(SessionStatus.loading);
  }

  Future<void> restore() async {
    state = SessionState(SessionStatus.loading, epoch: _epoch);
    try {
      if (_clearRequired) {
        await _forget();
        return;
      }
      _tokens = await _store.read();
      if (_tokens == null) {
        state = SessionState(SessionStatus.signedOut, epoch: _epoch);
        return;
      }
      await authorized('GET', '/account/me');
      state = SessionState(SessionStatus.signedIn, epoch: _epoch);
    } on ApiFailure catch (e) {
      if (state.status != SessionStatus.signedOut) {
        state = SessionState(
          SessionStatus.unavailable,
          error: e,
          epoch: _epoch,
        );
      }
    } catch (_) {
      state = SessionState(
        SessionStatus.unavailable,
        error: const ApiFailure('SECURE_STORAGE'),
        epoch: _epoch,
      );
    }
  }

  Future<void> login(String email, String password) async {
    final tokens = await _auth.login(email, password);
    try {
      await _store.write(tokens);
    } catch (_) {
      try {
        await _auth.logout(tokens.refreshToken);
      } catch (_) {}
      throw const ApiFailure('SECURE_STORAGE');
    }
    _tokens = tokens;
    _epoch++;
    state = SessionState(SessionStatus.signedIn, epoch: _epoch);
  }

  Future<void> _refresh() async {
    final previous = _tokens;
    if (previous == null) throw const ApiFailure('UNAUTHORIZED', status: 401);
    try {
      final next = await _auth.refresh(previous.refreshToken);
      if (!identical(previous, _tokens)) return;
      await _store.write(next);
      _tokens = next;
    } catch (_) {
      // A timed-out rotation may already have consumed the old token. Never replay it.
      await _forget();
      rethrow;
    }
  }

  Future<Map<String, dynamic>> authorized(
    String method,
    String path, {
    Map<String, dynamic>? body,
    Map<String, String> headers = const {},
  }) async {
    final original = _tokens;
    if (original == null) throw const ApiFailure('UNAUTHORIZED', status: 401);
    try {
      return await _api.request(
        method,
        path,
        body: body,
        headers: headers,
        token: original.accessToken,
      );
    } on ApiFailure catch (e) {
      if (e.status != 401) rethrow;
    }
    if (identical(original, _tokens)) {
      final pending = _refreshing ??= _refresh();
      try {
        await pending;
      } finally {
        if (identical(_refreshing, pending)) _refreshing = null;
      }
    }
    final current = _tokens;
    if (current == null) throw const ApiFailure('UNAUTHORIZED', status: 401);
    try {
      return await _api.request(
        method,
        path,
        body: body,
        headers: headers,
        token: current.accessToken,
      );
    } on ApiFailure catch (e) {
      if (e.status == 401) await _forget();
      rethrow;
    }
  }

  Future<void> _forget() async {
    _tokens = null;
    _clearRequired = true;
    _epoch++;
    state = SessionState(SessionStatus.loading, epoch: _epoch);
    try {
      await _store.clear();
      _clearRequired = false;
    } catch (_) {
      state = SessionState(
        SessionStatus.unavailable,
        error: const ApiFailure('SECURE_STORAGE'),
        epoch: _epoch,
      );
      throw const ApiFailure('SECURE_STORAGE');
    }
    state = SessionState(SessionStatus.signedOut, epoch: _epoch);
  }

  Future<void> logout() async {
    // Wait for an already running rotation before revoking its latest token.
    try {
      await _refreshing;
    } catch (_) {}
    final tokens = _tokens;
    ApiFailure? failure;
    try {
      if (tokens != null) {
        await _auth.logout(tokens.refreshToken);
      }
    } on ApiFailure catch (e) {
      failure = e;
    } finally {
      await _forget();
    }
    if (failure != null) throw failure;
  }
}
