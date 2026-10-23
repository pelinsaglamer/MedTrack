import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'features/auth/session.dart';
import 'features/auth/login_screen.dart';
import 'features/auth/register_screen.dart';
import 'features/medications/medication_form_screen.dart';
import 'features/medications/medication_detail_screen.dart';
import 'features/medications/schedule_form_screen.dart';
import 'features/online/dashboard_screen.dart';
import 'features/online/dose_detail_screen.dart';
import 'features/online/inventory_screen.dart';
import 'features/online/summary_screen.dart';
import 'features/medications/medication_list_screen.dart';
import 'shared/design.dart';
import 'features/notifications/notification_host.dart';
import 'features/notifications/notification_settings_screen.dart';

final routerProvider = Provider<GoRouter>((ref) {
  final refresh = ValueNotifier<int>(0);
  ref.listen(sessionProvider, (_, next) => refresh.value++);
  final router = GoRouter(
    initialLocation: '/startup',
    refreshListenable: refresh,
    redirect: (context, state) {
      final status = ref.read(sessionProvider).status;
      if (status == SessionStatus.signedOut && state.uri.path == '/register') {
        return null;
      }
      if (status == SessionStatus.signedIn &&
          !['/startup', '/login', '/register'].contains(state.uri.path)) {
        return null;
      }
      final target = switch (status) {
        SessionStatus.loading || SessionStatus.unavailable => '/startup',
        SessionStatus.signedOut => '/login',
        SessionStatus.signedIn => '/medications',
      };
      return state.uri.path == target ? null : target;
    },
    routes: [
      GoRoute(
        path: '/notifications',
        builder: (_, _) => const NotificationSettingsScreen(),
      ),
      GoRoute(
        path: '/today',
        builder: (_, _) => const DashboardScreen(),
        routes: [
          GoRoute(
            path: 'doses/:id',
            builder: (_, s) => DoseDetailScreen(id: s.pathParameters['id']!),
          ),
        ],
      ),
      GoRoute(path: '/summary', builder: (_, _) => const SummaryScreen()),
      GoRoute(
        path: '/medications',
        builder: (_, _) => const MedicationListScreen(),
        routes: [
          GoRoute(path: 'new', builder: (_, _) => const MedicationFormScreen()),
          GoRoute(
            path: ':id',
            builder: (_, s) =>
                MedicationDetailScreen(id: s.pathParameters['id']!),
            routes: [
              GoRoute(
                path: 'edit',
                builder: (_, s) =>
                    MedicationFormScreen(id: s.pathParameters['id']!),
              ),
              GoRoute(
                path: 'plan',
                builder: (_, s) =>
                    ScheduleFormScreen(id: s.pathParameters['id']!),
              ),
              GoRoute(
                path: 'inventory',
                builder: (_, s) => InventoryScreen(id: s.pathParameters['id']!),
              ),
            ],
          ),
        ],
      ),
      GoRoute(path: '/startup', builder: (_, _) => const StartupScreen()),
      GoRoute(path: '/login', builder: (_, _) => const LoginScreen()),
      GoRoute(path: '/register', builder: (_, _) => const RegisterScreen()),
    ],
    errorBuilder: (context, state) => Scaffold(
      appBar: AppBar(title: const Text('Sayfa bulunamadı')),
      body: Center(
        child: TextButton(
          onPressed: () => context.go('/medications'),
          child: const Text('İlaçlarıma dön'),
        ),
      ),
    ),
  );
  ref.onDispose(() {
    router.dispose();
    refresh.dispose();
  });
  return router;
});

class MedTrackApp extends ConsumerWidget {
  const MedTrackApp({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => NotificationHost(
    onOpen: (id) => ref.read(routerProvider).go('/today/doses/$id'),
    child: MaterialApp.router(
      title: 'MedTrack',
      debugShowCheckedModeBanner: false,
      locale: const Locale('tr'),
      supportedLocales: const [Locale('tr')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      theme: MedTrackDesign.theme(Brightness.light),
      darkTheme: MedTrackDesign.theme(Brightness.dark),
      routerConfig: ref.watch(routerProvider),
    ),
  );
}

class StartupScreen extends ConsumerWidget {
  const StartupScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionProvider);
    return Scaffold(
      body: ContentFrame(
        child: session.status == SessionStatus.unavailable
            ? FailureView(
                message: session.error!.message,
                onRetry: () => ref.read(sessionProvider.notifier).restore(),
              )
            : const Center(
                child: CircularProgressIndicator(
                  semanticsLabel: 'Oturum kontrol ediliyor',
                ),
              ),
      ),
    );
  }
}
