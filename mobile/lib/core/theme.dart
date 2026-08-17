import 'package:flutter/material.dart';

/// GitHub-inspired dark palette requested for Student League clients.
class AppColors {
  static const bg = Color(0xFF0D1117);
  static const surface = Color(0xFF161B22);
  static const surfaceHover = Color(0xFF1C2129);
  static const text = Color(0xFFC9D1D9);
  static const textStrong = Color(0xFFE6EDF3);
  static const muted = Color(0xFF8B949E);
  static const accent = Color(0xFF58A6FF);
  static const success = Color(0xFF3FB950);
  static const danger = Color(0xFFF85149);
  static const line = Color(0xFF30363D);
}

ThemeData buildAppTheme() {
  const base = ColorScheme.dark(
    surface: AppColors.surface,
    primary: AppColors.accent,
    secondary: AppColors.success,
    error: AppColors.danger,
    onPrimary: AppColors.bg,
    onSecondary: AppColors.bg,
    onSurface: AppColors.text,
  );

  return ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    colorScheme: base,
    scaffoldBackgroundColor: AppColors.bg,
    appBarTheme: const AppBarTheme(
      backgroundColor: AppColors.bg,
      foregroundColor: AppColors.textStrong,
      elevation: 0,
      centerTitle: false,
    ),
    cardTheme: CardThemeData(
      color: AppColors.surface,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: AppColors.line),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.accent,
        foregroundColor: AppColors.bg,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
      ),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: AppColors.surface,
        foregroundColor: AppColors.textStrong,
        side: const BorderSide(color: AppColors.line),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    ),
    textTheme: const TextTheme(
      displaySmall: TextStyle(color: AppColors.textStrong, fontWeight: FontWeight.w700),
      displayMedium: TextStyle(color: AppColors.textStrong, fontWeight: FontWeight.w700),
      titleLarge: TextStyle(color: AppColors.textStrong, fontWeight: FontWeight.w600),
      bodyLarge: TextStyle(color: AppColors.text),
      bodyMedium: TextStyle(color: AppColors.muted),
    ),
    dividerColor: AppColors.line,
  );
}
