import 'package:flutter/material.dart';

import '../core/brand.dart';

class BrandMark extends StatelessWidget {
  const BrandMark({super.key, this.size = 28, this.radius});

  final double size;
  final double? radius;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius ?? size / 2),
      child: Image.asset(
        brandMarkAsset,
        width: size,
        height: size,
        fit: BoxFit.cover,
        gaplessPlayback: true,
      ),
    );
  }
}
