import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/session_cleanup.dart';
import '../auth/session.dart';
import 'notification_controller.dart';

class NotificationHost extends ConsumerStatefulWidget {
  const NotificationHost({
    super.key,
    required this.child,
    required this.onOpen,
  });
  final Widget child;
  final ValueChanged<String> onOpen;
  @override
  ConsumerState<NotificationHost> createState() => _NotificationHostState();
}

class _NotificationHostState extends ConsumerState<NotificationHost>
    with WidgetsBindingObserver {
  late final Future<void> Function() _cleanup;
  late final SessionCleanup _registry;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    final controller = ref.read(notificationControllerProvider.notifier);
    _cleanup = controller.endSession;
    _registry = ref.read(sessionCleanupProvider);
    _registry.add(_cleanup);
    ref.listenManual(sessionProvider, (previous, next) {
      if (next.status == SessionStatus.signedIn &&
          (previous?.status != SessionStatus.signedIn ||
              previous?.epoch != next.epoch)) {
        unawaited(controller.activate());
      }
      if (next.status == SessionStatus.signedOut) {
        unawaited(controller.endSession().catchError((Object _) {}));
      }
    }, fireImmediately: true);
    ref.listenManual(notificationControllerProvider, (previous, next) {
      if (next.openDose != null &&
          ref.read(sessionProvider).status == SessionStatus.signedIn) {
        widget.onOpen(next.openDose!);
        controller.acknowledgeOpen();
      }
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed &&
        ref.read(sessionProvider).status == SessionStatus.signedIn) {
      unawaited(ref.read(notificationControllerProvider.notifier).sync());
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _registry.remove(_cleanup);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
