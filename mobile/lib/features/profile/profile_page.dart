import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/format.dart';
import '../../core/theme.dart';
import '../../state/auth_controller.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  final email = TextEditingController();
  final password = TextEditingController();

  @override
  void dispose() {
    email.dispose();
    password.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthController>();
    if (!auth.isAuthenticated) {
      return ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text('Профиль', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: AppColors.navy)),
          const SizedBox(height: 8),
          const Text('Войдите, чтобы видеть роли, избранное и пульт судьи.', style: TextStyle(color: AppColors.muted)),
          const SizedBox(height: 18),
          TextField(controller: email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Почта')),
          const SizedBox(height: 10),
          TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: 'Пароль')),
          if (auth.error != null) ...[
            const SizedBox(height: 10),
            Text(auth.error!, style: const TextStyle(color: AppColors.danger)),
          ],
          const SizedBox(height: 16),
          FilledButton(
            onPressed: auth.busy ? null : () => auth.login(email.text, password.text),
            child: Text(auth.busy ? 'Входим…' : 'Войти'),
          ),
        ],
      );
    }

    final user = auth.user!;
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Row(
          children: [
            CircleAvatar(
              radius: 32,
              backgroundColor: const Color(0x294CB4E5),
              backgroundImage: user.photoUrl != null && user.photoUrl!.isNotEmpty ? NetworkImage(user.photoUrl!) : null,
              child: user.photoUrl == null || user.photoUrl!.isEmpty
                  ? Text(initials(user.displayName), style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy))
                  : null,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('ПРОФИЛЬ', style: TextStyle(color: AppColors.ice, fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 0.8)),
                  Text(user.displayName, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: AppColors.navy)),
                  Wrap(
                    spacing: 6,
                    children: [
                      for (final role in user.roles)
                        Chip(
                          label: Text(roleLabels[role] ?? role, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700)),
                          backgroundColor: const Color(0x294CB4E5),
                          visualDensity: VisualDensity.compact,
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),
        if (auth.canOfficiate)
          _Tile(title: 'Пульт судьи', onTap: () => context.push('/referee')),
        if (auth.canManageLeague)
          const Padding(
            padding: EdgeInsets.only(bottom: 8),
            child: Text('Админка пока в вебе.', style: TextStyle(color: AppColors.muted)),
          ),
        const SizedBox(height: 8),
        OutlinedButton(
          onPressed: auth.logout,
          child: const Text('Выйти'),
        ),
      ],
    );
  }
}

class _Tile extends StatelessWidget {
  const _Tile({required this.title, required this.onTap});
  final String title;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Material(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: onTap,
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppColors.line),
            ),
            child: Text(title, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
          ),
        ),
      ),
    );
  }
}
