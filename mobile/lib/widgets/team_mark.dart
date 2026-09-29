import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/format.dart';
import '../core/theme.dart';

class TeamMark extends StatelessWidget {
  const TeamMark({super.key, this.url, required this.name, this.size = 18});

  final String? url;
  final String name;
  final double size;

  @override
  Widget build(BuildContext context) {
    final radius = size < 28 ? 4.0 : 8.0;
    final hasImage = url != null && url!.isNotEmpty;
    final inset = hasImage ? math.max(1.0, (radius * 0.3).ceilToDouble()) : 0.0;
    final fallback = Text(
      initials(name),
      style: TextStyle(
        fontWeight: FontWeight.w800,
        color: AppColors.navy,
        fontSize: (size * 0.38).clamp(7, 12),
      ),
    );
    return Container(
      width: size,
      height: size,
      padding: EdgeInsets.all(inset),
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: hasImage ? AppColors.surface : const Color(0x294CB4E5),
        borderRadius: BorderRadius.circular(radius),
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
