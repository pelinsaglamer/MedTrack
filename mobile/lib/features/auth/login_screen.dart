import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../shared/design.dart';
import 'session.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});
  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  bool _hide = true;
  ApiFailure? _error;
  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy || !_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref
          .read(sessionProvider.notifier)
          .login(_email.text, _password.text);
      TextInput.finishAutofillContext();
    } on ApiFailure catch (e) {
      if (mounted) setState(() => _error = e);
    } catch (_) {
      if (mounted) setState(() => _error = const ApiFailure('UNKNOWN'));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: ContentFrame(
      child: ListView(
        children: [
          const SizedBox(height: 40),
          Icon(
            Icons.medication_outlined,
            size: 56,
            color: Theme.of(context).colorScheme.primary,
          ),
          const SizedBox(height: 16),
          Text(
            'MedTrack',
            style: Theme.of(context).textTheme.headlineLarge,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 8),
          const Text(
            'İlaç planını ve stoklarını takip et.',
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 32),
          AutofillGroup(
            child: Form(
              key: _form,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    'Giriş yap',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 20),
                  TextFormField(
                    controller: _email,
                    enabled: !_busy,
                    decoration: const InputDecoration(labelText: 'E-posta'),
                    keyboardType: TextInputType.emailAddress,
                    textInputAction: TextInputAction.next,
                    autocorrect: false,
                    autofillHints: const [AutofillHints.username],
                    validator: (v) =>
                        v == null ||
                            !RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$')
                                .hasMatch(v.trim())
                        ? 'Geçerli bir e-posta girin.'
                        : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _password,
                    enabled: !_busy,
                    obscureText: _hide,
                    enableSuggestions: false,
                    autocorrect: false,
                    autofillHints: const [AutofillHints.password],
                    textInputAction: TextInputAction.done,
                    onFieldSubmitted: (_) => _submit(),
                    decoration: InputDecoration(
                      labelText: 'Şifre',
                      suffixIcon: IconButton(
                        tooltip: _hide ? 'Şifreyi göster' : 'Şifreyi gizle',
                        onPressed: () => setState(() => _hide = !_hide),
                        icon: Icon(
                          _hide
                              ? Icons.visibility_outlined
                              : Icons.visibility_off_outlined,
                        ),
                      ),
                    ),
                    validator: (v) =>
                        v == null || v.isEmpty ? 'Şifrenizi girin.' : null,
                  ),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      child: Semantics(
                        liveRegion: true,
                        child: Text(
                          _error!.message,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.error,
                          ),
                        ),
                      ),
                    ),
                  const SizedBox(height: 20),
                  FilledButton(
                    onPressed: _busy ? null : _submit,
                    child: Text(_busy ? 'Giriş yapılıyor…' : 'Giriş yap'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          const Text(
            'Bu uygulama tıbbi öneri sunmaz.',
            textAlign: TextAlign.center,
          ),
        ],
      ),
    ),
  );
}
