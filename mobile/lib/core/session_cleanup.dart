import 'package:flutter_riverpod/flutter_riverpod.dart';

final sessionCleanupProvider = Provider<SessionCleanup>(
  (ref) => SessionCleanup(),
);

class SessionCleanup {
  final Set<Future<void> Function()> _callbacks = {};
  void add(Future<void> Function() callback) => _callbacks.add(callback);
  void remove(Future<void> Function() callback) => _callbacks.remove(callback);
  Future<void> run() async {
    for (final callback in _callbacks.toList()) {
      await callback();
    }
  }
}
