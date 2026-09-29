import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/format.dart';
import '../core/theme.dart';

class PlayerPhoto extends StatelessWidget {
  const PlayerPhoto({
    super.key,
    this.url,
    required this.name,
    this.size = 56,
    this.tile = false,
  });

  final String? url;
  final String name;
  final double size;
  final bool tile;

  @override
  Widget build(BuildContext context) {
    final radius = tile ? BorderRadius.circular(size < 40 ? 8 : 12) : null;
    final hasImage = url != null && url!.isNotEmpty;
    final inset = hasImage ? (tile ? (size < 40 ? 3.0 : 4.0) : math.max(2.0, (size * 0.1465).ceilToDouble())) : 0.0;
    final fallback = Text(
      initials(name),
      style: TextStyle(
        fontWeight: FontWeight.w800,
        color: AppColors.navy,
        fontSize: size * 0.32,
      ),
    );
    return Container(
      width: size,
      height: size,
      padding: EdgeInsets.all(inset),
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: hasImage ? AppColors.surface : const Color(0x294CB4E5),
        shape: tile ? BoxShape.rectangle : BoxShape.circle,
        borderRadius: radius,
      ),
      child: !hasImage
          ? fallback
          : Image.network(
              url!,
              fit: BoxFit.contain,
              width: double.infinity,
              height: double.infinity,
              filterQuality: FilterQuality.medium,
              errorBuilder: (_, __, ___) => fallback,
            ),
    );
  }
}
