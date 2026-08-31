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
    return ClipRRect(
      borderRadius: BorderRadius.circular(size < 28 ? 4 : 8),
      child: Container(
        width: size,
        height: size,
        color: const Color(0x294CB4E5),
        alignment: Alignment.center,
        child: logoUrl == null || logoUrl!.isEmpty
            ? Text(
                initials(name),
                style: TextStyle(
                  fontSize: size * 0.38,
                  fontWeight: FontWeight.w800,
                  color: AppColors.navy,
                ),
              )
            : Image.network(
                logoUrl!,
                width: size,
                height: size,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => Text(
                  initials(name),
                  style: TextStyle(fontSize: size * 0.38, fontWeight: FontWeight.w800, color: AppColors.navy),
                ),
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
    return ClipOval(
      child: Container(
        width: size,
        height: size,
        color: const Color(0x294CB4E5),
        alignment: Alignment.center,
        child: photoUrl == null || photoUrl!.isEmpty
            ? Text(
                initials(name),
                style: TextStyle(fontSize: size * 0.32, fontWeight: FontWeight.w800, color: AppColors.navy),
              )
            : Image.network(
                photoUrl!,
                width: size,
                height: size,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => Text(
                  initials(name),
                  style: TextStyle(fontSize: size * 0.32, fontWeight: FontWeight.w800, color: AppColors.navy),
                ),
              ),
      ),
    );
  }
}
