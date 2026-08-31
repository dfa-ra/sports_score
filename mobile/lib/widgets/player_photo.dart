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
    final image = url != null && url!.isNotEmpty ? NetworkImage(url!) : null;
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: const Color(0x294CB4E5),
        shape: tile ? BoxShape.rectangle : BoxShape.circle,
        borderRadius: radius,
        image: image == null ? null : DecorationImage(image: image, fit: BoxFit.cover),
      ),
      child: image == null
          ? Text(
              initials(name),
              style: TextStyle(
                fontWeight: FontWeight.w800,
                color: AppColors.navy,
                fontSize: size * 0.32,
              ),
            )
          : null,
    );
  }
}
