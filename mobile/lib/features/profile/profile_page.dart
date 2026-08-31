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
import '../../widgets/player_card_sheet.dart';
import '../../widgets/player_photo.dart';
import '../../widgets/team_mark.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  final email = TextEditingController();
  final password = TextEditingController();
  final firstName = TextEditingController();
  final lastName = TextEditingController();
  final displayName = TextEditingController();
  final jersey = TextEditingController();
  final position = TextEditingController();
  final bio = TextEditingController();
  final server = TextEditingController();
  PlayerProfile? profile;
  PlayerCard? card;
  bool saving = false;
  String? formError;
  String? formOk;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      server.text = context.read<AuthController>().api.baseUrl;
      _loadProfile();
    });
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
    server.dispose();
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
        await _loadCard(next.id);
      }
    } catch (_) {
      if (mounted) setState(() => profile = PlayerProfile());
    }
  }

  Future<void> _loadCard(String? id) async {
    if (id == null) {
      if (mounted) setState(() => card = null);
      return;
    }
    try {
      final data = await context.read<AuthController>().api.get('/players/$id/card');
      if (data is Map && mounted) {
        setState(() => card = PlayerCard.fromJson(Map<String, dynamic>.from(data)));
      }
    } catch (_) {
      if (mounted) setState(() => card = null);
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
      final data = await context.read<AuthController>().api.put('/players/me', body);
      if (data is Map && mounted) {
        final next = PlayerProfile.fromJson(Map<String, dynamic>.from(data));
        setState(() {
          profile = next;
          formOk = 'Профиль сохранён.';
        });
        await _loadCard(next.id);
      }
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      if (mounted) setState(() => formError = e is ApiException ? e.message : 'Профиль не сохранился.');
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Future<void> _saveServer() async {
    final auth = context.read<AuthController>();
    if (!auth.canManageLeague) return;
    final league = context.read<LeagueStore>();
    await auth.api.setAdminBaseUrl(server.text);
    server.text = auth.api.baseUrl;
    await league.load();
    if (mounted) setState(() {});
  }

  Future<void> _openEdit() async {
    formError = null;
    formOk = null;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setSheet) {
            return Padding(
              padding: EdgeInsets.fromLTRB(16, 0, 16, 16 + MediaQuery.of(context).viewInsets.bottom),
              child: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(profile?.id != null ? 'Изменить анкету' : 'Стать игроком', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: AppColors.navy)),
                    const SizedBox(height: 12),
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
                    const SizedBox(height: 14),
                    FilledButton(
                      onPressed: saving
                          ? null
                          : () async {
                              await _save();
                              setSheet(() {});
                            },
                      child: Text(saving ? 'Сохраняем…' : profile?.id != null ? 'Сохранить' : 'Стать игроком'),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  Widget _serverCard() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Сервер', style: TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
        const SizedBox(height: 6),
        const Text('Только админ. Можно переключить API, например с прода на dev.', style: TextStyle(color: AppColors.muted, fontSize: 12)),
        const SizedBox(height: 10),
        TextField(controller: server, keyboardType: TextInputType.url, decoration: const InputDecoration(labelText: 'API')),
        const SizedBox(height: 10),
        OutlinedButton(onPressed: _saveServer, child: const Text('Сохранить и обновить')),
      ],
    );
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
                    final league = context.read<LeagueStore>();
                    final ok = await auth.login(email.text, password.text);
                    if (!ok || !mounted) return;
                    if (auth.canManageLeague) {
                      server.text = auth.api.baseUrl;
                      await league.load();
                    }
                    await _loadProfile();
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
    final currentTeamId = card?.teamId;
    final favTeams = fav.teams
        .where((id) => id != currentTeamId)
        .map((id) => (id: id, name: store.teamName(id), logo: store.teams[id]?.logoUrl))
        .toList();

    return ListView(
      children: [
        if (card != null)
          PlayerCardSheet(card: card!, onEdit: _openEdit, resolveMedia: auth.api.resolveMedia)
        else
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 8, 8),
            child: Row(
              children: [
                PlayerPhoto(
                  url: auth.api.resolveMedia(user.photoUrl ?? profile?.avatarUrl),
                  name: displayName.text.isNotEmpty ? displayName.text : user.displayName,
                  size: 76,
                  tile: true,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        displayName.text.isNotEmpty ? displayName.text : user.displayName,
                        style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: AppColors.navy),
                      ),
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
                IconButton(tooltip: 'Изменить', onPressed: _openEdit, icon: const Icon(Icons.edit_outlined, color: AppColors.navy)),
              ],
            ),
          ),
        const LeagueHead(title: 'Команды'),
        if (card?.teamId != null)
          ListTile(
            leading: TeamMark(url: auth.api.resolveMedia(card!.teamLogoUrl), name: card!.teamName ?? 'Команда', size: 22),
            title: Text(card!.teamName ?? 'Команда', style: const TextStyle(fontWeight: FontWeight.w700, color: AppColors.navy)),
            trailing: const Icon(Icons.chevron_right, color: AppColors.muted),
            onTap: () => context.push('/teams/${card!.teamId}'),
          ),
        for (final team in favTeams)
          ListTile(
            leading: TeamMark(url: auth.api.resolveMedia(team.logo), name: team.name, size: 22),
            title: Text(team.name, style: const TextStyle(fontWeight: FontWeight.w700, color: AppColors.navy)),
            trailing: const Icon(Icons.chevron_right, color: AppColors.muted),
            onTap: () => context.push('/teams/${team.id}'),
          ),
        if (card?.teamId == null && favTeams.isEmpty)
          const Padding(
            padding: EdgeInsets.all(14),
            child: Text('Звезда на карточке команды — и она будет здесь.', style: TextStyle(color: AppColors.muted)),
          ),
        if (favMatches.isNotEmpty) ...[
          const LeagueHead(title: 'Избранные матчи'),
          for (final match in favMatches)
            MatchRow(
              match: match,
              homeName: store.teamName(match.homeTeamId),
              awayName: store.teamName(match.awayTeamId),
            ),
        ],
        if (auth.canOfficiate)
          ListTile(
            title: const Text('Пульт судьи', style: TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
            trailing: const Icon(Icons.chevron_right, color: AppColors.muted),
            onTap: () => context.push('/referee'),
          ),
        if (auth.canManageLeague)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: _serverCard(),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
          child: OutlinedButton(onPressed: auth.logout, child: const Text('Выйти')),
        ),
      ],
    );
  }
}
