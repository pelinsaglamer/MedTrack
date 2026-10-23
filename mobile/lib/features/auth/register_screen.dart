import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../core/providers.dart';
import '../../shared/design.dart';
import 'auth_api.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});
  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _confirmation = TextEditingController();
  final _timezone = TextEditingController(text: 'Europe/Istanbul');
  bool _busy = false;
  bool _hide = true;
  ApiFailure? _error;
  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _confirmation.dispose();
    _timezone.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy || !_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await AuthApi(ref.read(apiProvider))
          .register(_email.text, _password.text, _timezone.text);
      if (!mounted) return;
      final messenger = ScaffoldMessenger.of(context);
      context.go('/login');
      messenger.showSnackBar(
        const SnackBar(
          content: Text(
            'Hesabın oluşturuldu. E-posta ve şifrenle giriş yapabilirsin.',
          ),
        ),
      );
    } on ApiFailure catch (e) {
      if (mounted) setState(() => _error = e);
    } catch (_) {
      if (mounted) setState(() => _error = const ApiFailure('UNKNOWN'));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String? _serverError(String name) => _error?.fields[name];
  @override
  Widget build(BuildContext context) => PopScope(
    canPop: !_busy,
    child: Scaffold(
      appBar: AppBar(
        title: const Text('Kayıt ol'),
        leading: IconButton(
          tooltip: 'Girişe dön',
          onPressed: _busy ? null : () => context.go('/login'),
          icon: const Icon(Icons.arrow_back),
        ),
      ),
      body: ContentFrame(
        child: ListView(
          children: [
            Text(
              'MedTrack hesabını oluştur',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 8),
            const Text('İlaç planlarını kendi hesabında takip et.'),
            const SizedBox(height: 24),
            AutofillGroup(
              child: Form(
                key: _form,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    TextFormField(
                      controller: _email,
                      enabled: !_busy,
                      decoration: InputDecoration(
                        labelText: 'E-posta',
                        errorText: _serverError('email'),
                      ),
                      keyboardType: TextInputType.emailAddress,
                      textInputAction: TextInputAction.next,
                      autofillHints: const [AutofillHints.newUsername],
                      autocorrect: false,
                      validator: (v) =>
                          v == null ||
                              v.trim().length > 254 ||
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
                      autocorrect: false,
                      enableSuggestions: false,
                      autofillHints: const [AutofillHints.newPassword],
                      textInputAction: TextInputAction.next,
                      decoration: InputDecoration(
                        labelText: 'Şifre',
                        helperText: 'En az 12 karakter kullan.',
                        errorText: _serverError('password'),
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
                      validator: (v) => v == null || v.runes.length < 12
                          ? 'En az 12 karakterli bir şifre girin.'
                          : utf8.encode(v).length > 72
                          ? 'Şifre çok uzun; daha kısa bir şifre seçin.'
                          : null,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _confirmation,
                      enabled: !_busy,
                      obscureText: _hide,
                      autocorrect: false,
                      enableSuggestions: false,
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'Şifre tekrar',
                      ),
                      validator: (v) =>
                          v == null || v.isEmpty || v != _password.text
                          ? 'Şifreler aynı olmalı.'
                          : null,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _timezone,
                      enabled: !_busy,
                      autocorrect: false,
                      textInputAction: TextInputAction.done,
                      onFieldSubmitted: (_) => _submit(),
                      decoration: InputDecoration(
                        labelText: 'Saat dilimi',
                        helperText: 'Türkiye: Europe/Istanbul. Başka bir yerdeysen IANA bölgeni gir.',
                        helperMaxLines: 3,
                        errorText: _serverError('timezone'),
                      ),
                      validator: (v) => v == null || v.trim().isEmpty
                          ? 'Saat dilimini girin.'
                          : null,
                    ),
                    if (_error != null)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        child: Semantics(
                          liveRegion: true,
                          child: Text(
                            _error!.code == 'RESOURCE_CONFLICT'
                                ? 'Bu e-posta ile hesap oluşturulamadı. Mevcut hesabınla giriş yapmayı deneyebilirsin.'
                                : _error!.message,
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.error,
                            ),
                          ),
                        ),
                      ),
                    const SizedBox(height: 24),
                    FilledButton(
                      onPressed: _busy ? null : _submit,
                      child: Text(
                        _busy ? 'Hesap oluşturuluyor…' : 'Hesap oluştur',
                      ),
                    ),
                    TextButton(
                      onPressed: _busy ? null : () => context.go('/login'),
                      child: const Text('Zaten hesabın var mı? Giriş yap'),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
