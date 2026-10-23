import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'features/auth/session.dart';
import 'features/auth/login_screen.dart';
import 'features/auth/register_screen.dart';
import 'features/medications/medication_list_screen.dart';
import 'shared/design.dart';

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
      final target = switch (status) {
        SessionStatus.loading || SessionStatus.unavailable => '/startup',
        SessionStatus.signedOut => '/login',
        SessionStatus.signedIn => '/medications',
      };
      return state.uri.path == target ? null : target;
    },
    routes: [
      GoRoute(path: '/startup', builder: (_, _) => const StartupScreen()),
      GoRoute(path: '/login', builder: (_, _) => const LoginScreen()),
      GoRoute(path: '/register', builder: (_, _) => const RegisterScreen()),
      GoRoute(
        path: '/medications',
        builder: (_, _) => const MedicationListScreen(),
      ),
    ],
    errorBuilder: (context, state) => const StartupScreen(),
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
  Widget build(BuildContext context, WidgetRef ref) => MaterialApp.router(
    title: 'MedTrack',
    debugShowCheckedModeBanner: false,
    locale: const Locale('tr'),
    supportedLocales: const [Locale('tr')],
    localizationsDelegates: GlobalMaterialLocalizations.delegates,
    theme: MedTrackDesign.theme(Brightness.light),
    darkTheme: MedTrackDesign.theme(Brightness.dark),
    routerConfig: ref.watch(routerProvider),
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
