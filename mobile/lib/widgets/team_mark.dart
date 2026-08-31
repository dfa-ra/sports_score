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
    final image = url != null && url!.isNotEmpty ? NetworkImage(url!) : null;
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: const Color(0x294CB4E5),
        borderRadius: BorderRadius.circular(size < 28 ? 4 : 8),
        image: image == null ? null : DecorationImage(image: image, fit: BoxFit.cover),
      ),
      child: image == null
          ? Text(
              initials(name),
              style: TextStyle(
                fontWeight: FontWeight.w800,
                color: AppColors.navy,
                fontSize: (size * 0.38).clamp(7, 12),
              ),
            )
          : null,
    );
  }
}
