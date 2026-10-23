import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class Tokens {
  const Tokens(this.accessToken, this.refreshToken);
  factory Tokens.fromJson(Map<String, dynamic> json) =>
      Tokens(json['accessToken'] as String, json['refreshToken'] as String);
  final String accessToken;
  final String refreshToken;
  Map<String, dynamic> toJson() => {
    'accessToken': accessToken,
    'refreshToken': refreshToken,
  };
  @override
  String toString() => 'Tokens(REDACTED)';
}

abstract interface class TokenStore {
  Future<Tokens?> read();
  Future<void> write(Tokens tokens);
  Future<void> clear();
}

class SecureTokenStore implements TokenStore {
  SecureTokenStore(this.storage);
  final FlutterSecureStorage storage;
  static const _key = 'medtrack.session.v1';
  @override
  Future<Tokens?> read() async {
    final raw = await storage.read(key: _key);
    if (raw == null) return null;
    try {
      return Tokens.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    } on FormatException {
      await clear();
      return null;
    } on TypeError {
      await clear();
      return null;
    }
  }

  @override
  Future<void> write(Tokens tokens) =>
      storage.write(key: _key, value: jsonEncode(tokens.toJson()));
  @override
  Future<void> clear() => storage.delete(key: _key);
}
