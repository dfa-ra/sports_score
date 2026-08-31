import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/theme.dart';
import '../../state/favorites_store.dart';

class ShellPage extends StatelessWidget {
  const ShellPage({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    final favCount = context.watch<FavoritesStore>().count;
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 16,
        title: Row(
          children: [
            Container(
              width: 28,
              height: 28,
              alignment: Alignment.center,
              decoration: const BoxDecoration(color: AppColors.ice, shape: BoxShape.circle),
              child: const Text('SL', style: TextStyle(color: AppColors.navy, fontWeight: FontWeight.w800, fontSize: 11)),
            ),
            const SizedBox(width: 8),
            const Text('KRONBARS', style: TextStyle(fontWeight: FontWeight.w800, letterSpacing: 0.4)),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Профиль',
            onPressed: () => navigationShell.goBranch(3),
            icon: const Icon(Icons.person_outline),
          ),
        ],
      ),
      body: navigationShell,
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: navigationShell.currentIndex,
        onTap: navigationShell.goBranch,
        items: [
          const BottomNavigationBarItem(icon: Icon(Icons.grid_view_outlined), label: 'Игры'),
          const BottomNavigationBarItem(icon: Icon(Icons.menu), label: 'Таблица'),
          const BottomNavigationBarItem(
            icon: Icon(Icons.wifi_tethering, color: AppColors.ice),
            activeIcon: Icon(Icons.wifi_tethering, color: AppColors.ice),
            label: 'Live',
          ),
          BottomNavigationBarItem(
            icon: _ProfileIcon(count: favCount),
            label: 'Профиль',
          ),
        ],
      ),
    );
  }
}

class _ProfileIcon extends StatelessWidget {
  const _ProfileIcon({required this.count});
  final int count;

  @override
  Widget build(BuildContext context) {
    const icon = Icon(Icons.person_outline);
    if (count == 0) return icon;
    return Badge(
      backgroundColor: AppColors.navy,
      label: Text('$count', style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w800)),
      child: icon,
    );
  }
}
