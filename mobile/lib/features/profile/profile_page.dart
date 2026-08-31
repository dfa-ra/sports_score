import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/api_client.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/theme.dart';
import '../../state/auth_controller.dart';
import '../../state/favorites_store.dart';
import '../../state/league_store.dart';
import '../../widgets/match_row.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> with SingleTickerProviderStateMixin {
  final email = TextEditingController();
  final password = TextEditingController();
  final firstName = TextEditingController();
  final lastName = TextEditingController();
  final displayName = TextEditingController();
  final jersey = TextEditingController();
  final position = TextEditingController();
  final bio = TextEditingController();
  late final TabController _tabs;
  PlayerProfile? profile;
  bool saving = false;
  String? formError;
  String? formOk;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 2, vsync: this);
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadProfile());
  }

  @override
  void dispose() {
    email.dispose();
    password.dispose();
    firstName.dispose();
    lastName.dispose();
    displayName.dispose();
    jersey.dispose();
    position.dispose();
    bio.dispose();
    _tabs.dispose();
    super.dispose();
  }

  Future<void> _loadProfile() async {
    final auth = context.read<AuthController>();
    if (!auth.isAuthenticated) return;
    try {
      final data = await auth.api.get('/players/me');
      if (data is Map && mounted) {
        final next = PlayerProfile.fromJson(Map<String, dynamic>.from(data));
        firstName.text = next.firstName;
        lastName.text = next.lastName;
        displayName.text = next.displayName;
        jersey.text = next.jerseyNumber?.toString() ?? '';
        position.text = next.position;
        bio.text = next.bio;
        setState(() => profile = next);
      }
    } catch (_) {
      if (mounted) setState(() => profile = PlayerProfile());
    }
  }

  Future<void> _save() async {
    setState(() {
      saving = true;
      formError = null;
      formOk = null;
    });
    try {
      final body = PlayerProfile(
        firstName: firstName.text.trim(),
        lastName: lastName.text.trim(),
        displayName: displayName.text.trim(),
        jerseyNumber: int.tryParse(jersey.text),
        position: position.text.trim(),
        bio: bio.text.trim(),
      ).toRequest();
      await context.read<AuthController>().api.put('/players/me', body);
      if (mounted) setState(() => formOk = 'Профиль сохранён.');
    } catch (e) {
      if (mounted) setState(() => formError = e is ApiException ? e.message : 'Профиль не сохранился.');
    } finally {
      if (mounted) setState(() => saving = false);
    }
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
            onPressed: auth.busy
                ? null
                : () async {
                    final ok = await auth.login(email.text, password.text);
                    if (ok) await _loadProfile();
                  },
            child: Text(auth.busy ? 'Входим…' : 'Войти'),
          ),
        ],
      );
    }

    final user = auth.user!;
    final store = context.watch<LeagueStore>();
    final fav = context.watch<FavoritesStore>();
    final favMatches = store.matches.where((m) => fav.hasMatch(m.id)).toList();
    final favTeams = fav.teams.map((id) => (id: id, name: store.teamName(id))).toList();

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Row(
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
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              if (profile?.id != null) _Shortcut(title: 'Карточка игрока', onTap: () => context.push('/players/${profile!.id}')),
              if (auth.canOfficiate) _Shortcut(title: 'Пульт судьи', onTap: () => context.push('/referee')),
              _Shortcut(title: 'Бомбардиры', onTap: () => context.go('/table')),
              if (auth.canManageLeague) const _Shortcut(title: 'Админка в вебе', onTap: null),
            ],
          ),
        ),
        TabBar(
          controller: _tabs,
          tabs: const [
            Tab(text: 'ИЗБРАННОЕ'),
            Tab(text: 'АНКЕТА'),
          ],
        ),
        Expanded(
          child: TabBarView(
            controller: _tabs,
            children: [
              ListView(
                children: [
                  const LeagueHead(title: 'Избранные команды'),
                  if (favTeams.isEmpty)
                    const Padding(
                      padding: EdgeInsets.all(14),
                      child: Text('Звезда на карточке команды — и она будет здесь.', style: TextStyle(color: AppColors.muted)),
                    )
                  else
                    for (final team in favTeams)
                      ListTile(
                        title: Text(team.name, style: const TextStyle(fontWeight: FontWeight.w700, color: AppColors.navy)),
                        trailing: const Icon(Icons.chevron_right, color: AppColors.muted),
                        onTap: () => context.push('/teams/${team.id}'),
                      ),
                  const LeagueHead(title: 'Избранные матчи'),
                  if (favMatches.isEmpty)
                    const Padding(
                      padding: EdgeInsets.all(14),
                      child: Text('Отмечайте игры звездой в календаре.', style: TextStyle(color: AppColors.muted)),
                    )
                  else
                    for (final match in favMatches)
                      MatchRow(
                        match: match,
                        homeName: store.teamName(match.homeTeamId),
                        awayName: store.teamName(match.awayTeamId),
                      ),
                ],
              ),
              ListView(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                children: [
                  TextField(controller: firstName, decoration: const InputDecoration(labelText: 'Имя')),
                  const SizedBox(height: 10),
                  TextField(controller: lastName, decoration: const InputDecoration(labelText: 'Фамилия')),
                  const SizedBox(height: 10),
                  TextField(controller: displayName, decoration: const InputDecoration(labelText: 'Как писать на майке')),
                  const SizedBox(height: 10),
                  TextField(controller: jersey, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Номер')),
                  const SizedBox(height: 10),
                  TextField(controller: position, decoration: const InputDecoration(labelText: 'Позиция')),
                  const SizedBox(height: 10),
                  TextField(controller: bio, maxLines: 3, decoration: const InputDecoration(labelText: 'О себе')),
                  if (formError != null) ...[
                    const SizedBox(height: 10),
                    Text(formError!, style: const TextStyle(color: AppColors.danger)),
                  ],
                  if (formOk != null) ...[
                    const SizedBox(height: 10),
                    Text(formOk!, style: const TextStyle(color: AppColors.win)),
                  ],
                  const SizedBox(height: 14),
                  FilledButton(
                    onPressed: saving ? null : _save,
                    child: Text(saving ? 'Сохраняем…' : profile?.id != null ? 'Обновить' : 'Стать игроком'),
                  ),
                ],
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: OutlinedButton(onPressed: auth.logout, child: const Text('Выйти')),
        ),
      ],
    );
  }
}

class _Shortcut extends StatelessWidget {
  const _Shortcut({required this.title, required this.onTap});
  final String title;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Container(
          width: 158,
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.line),
          ),
          child: Text(title, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
        ),
      ),
    );
  }
}
