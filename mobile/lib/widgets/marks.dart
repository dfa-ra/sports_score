import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/format.dart';
import '../core/theme.dart';

class TeamMark extends StatelessWidget {
  const TeamMark({super.key, required this.name, this.logoUrl, this.size = 18});

  final String name;
  final String? logoUrl;
  final double size;

  @override
  Widget build(BuildContext context) {
    final radius = size < 28 ? 4.0 : 8.0;
    final hasImage = logoUrl != null && logoUrl!.isNotEmpty;
    final inset = hasImage ? math.max(1.0, (radius * 0.3).ceilToDouble()) : 0.0;
    final fallback = Text(
      initials(name),
      style: TextStyle(
        fontSize: size * 0.38,
        fontWeight: FontWeight.w800,
        color: AppColors.navy,
      ),
    );
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: Container(
        width: size,
        height: size,
        padding: EdgeInsets.all(inset),
        color: hasImage ? AppColors.surface : const Color(0x294CB4E5),
        alignment: Alignment.center,
        child: !hasImage
            ? fallback
            : Image.network(
                logoUrl!,
                width: double.infinity,
                height: double.infinity,
                fit: BoxFit.contain,
                filterQuality: FilterQuality.medium,
                errorBuilder: (_, __, ___) => fallback,
              ),
      ),
    );
  }
}

class PlayerPhoto extends StatelessWidget {
  const PlayerPhoto({super.key, required this.name, this.photoUrl, this.size = 64});

  final String name;
  final String? photoUrl;
  final double size;

  @override
  Widget build(BuildContext context) {
    final hasImage = photoUrl != null && photoUrl!.isNotEmpty;
    final inset = hasImage ? math.max(2.0, (size * 0.1465).ceilToDouble()) : 0.0;
    final fallback = Text(
      initials(name),
      style: TextStyle(fontSize: size * 0.32, fontWeight: FontWeight.w800, color: AppColors.navy),
    );
    return ClipOval(
      child: Container(
        width: size,
        height: size,
        padding: EdgeInsets.all(inset),
        color: hasImage ? AppColors.surface : const Color(0x294CB4E5),
        alignment: Alignment.center,
        child: !hasImage
            ? fallback
            : Image.network(
                photoUrl!,
                width: double.infinity,
                height: double.infinity,
                fit: BoxFit.contain,
                filterQuality: FilterQuality.medium,
                errorBuilder: (_, __, ___) => fallback,
              ),
      ),
    );
  }
}
