import 'package:flutter/material.dart';

class AppColors {
  static const navy = Color(0xFF00205B);
  static const ice = Color(0xFF4CB4E5);
  static const canvas = Color(0xFFE8EEF4);
  static const surface = Color(0xFFFFFFFF);
  static const ink = Color(0xFF001433);
  static const text = Color(0xFF24344D);
  static const muted = Color(0xFF5C6D82);
  static const line = Color(0x1A00205B);
  static const danger = Color(0xFFE36B5B);
  static const win = Color(0xFF1B8A4A);
  static const draw = Color(0xFFC47B00);
}

ThemeData buildAppTheme() {
  const scheme = ColorScheme.light(
    primary: AppColors.navy,
    onPrimary: Colors.white,
    secondary: AppColors.ice,
    onSecondary: AppColors.navy,
    surface: AppColors.surface,
    onSurface: AppColors.text,
    error: AppColors.danger,
  );

  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: AppColors.canvas,
    fontFamily: 'Roboto',
    appBarTheme: const AppBarTheme(
      backgroundColor: AppColors.navy,
      foregroundColor: Colors.white,
      elevation: 0,
      centerTitle: false,
    ),
    dividerColor: AppColors.line,
    tabBarTheme: const TabBarThemeData(
      labelColor: AppColors.navy,
      unselectedLabelColor: AppColors.muted,
      indicatorColor: AppColors.ice,
      labelStyle: TextStyle(fontWeight: FontWeight.w800, fontSize: 13, letterSpacing: 0.6),
      unselectedLabelStyle: TextStyle(fontWeight: FontWeight.w800, fontSize: 13, letterSpacing: 0.6),
    ),
    bottomNavigationBarTheme: const BottomNavigationBarThemeData(
      backgroundColor: Colors.white,
      selectedItemColor: AppColors.navy,
      unselectedItemColor: AppColors.muted,
      type: BottomNavigationBarType.fixed,
      selectedLabelStyle: TextStyle(fontWeight: FontWeight.w700, fontSize: 11),
      unselectedLabelStyle: TextStyle(fontWeight: FontWeight.w700, fontSize: 11),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: const BorderSide(color: AppColors.line)),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: const BorderSide(color: AppColors.line)),
      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: const BorderSide(color: AppColors.ice, width: 2)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.ice,
        foregroundColor: AppColors.navy,
        textStyle: const TextStyle(fontWeight: FontWeight.w800),
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
  );
}
