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

  /// Accepted so older call sites compile. The photo is always a plain circle.
  final bool tile;

  @override
  Widget build(BuildContext context) {
    // `tile` used to draw a rounded square. The photo is always a plain circle.
    assert(tile == true || tile == false);
    final hasImage = url != null && url!.isNotEmpty;
    final fallback = Text(
      initials(name),
      style: TextStyle(
        fontWeight: FontWeight.w800,
        color: AppColors.navy,
        fontSize: size * 0.32,
      ),
    );
    return ClipOval(
      child: Container(
        width: size,
        height: size,
        alignment: Alignment.center,
        color: hasImage ? Colors.transparent : const Color(0x294CB4E5),
        child: !hasImage
            ? fallback
            : Image.network(
                url!,
                fit: BoxFit.cover,
                alignment: Alignment.center,
                width: size,
                height: size,
                filterQuality: FilterQuality.medium,
                errorBuilder: (_, __, ___) => fallback,
              ),
      ),
    );
  }
}
