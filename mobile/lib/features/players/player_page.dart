import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/models.dart';
import '../../core/theme.dart';
import '../../state/league_store.dart';
import '../../widgets/match_row.dart';
import '../../widgets/player_card_sheet.dart';

class PlayerPage extends StatefulWidget {
  const PlayerPage({super.key, required this.playerId});
  final String playerId;

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> {
  PlayerCard? card;
  bool loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final store = context.read<LeagueStore>();
      final data = await store.api.get('/players/${widget.playerId}/card');
      if (data is Map && mounted) {
        setState(() => card = PlayerCard.fromJson(Map<String, dynamic>.from(data)));
      }
    } catch (_) {
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator(color: AppColors.ice)));
    }
    final current = card;
    if (current == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Игрок')),
        body: const Center(child: EmptyHint(title: 'Карточка не найдена')),
      );
    }
    final api = context.read<LeagueStore>().api;
    return Scaffold(
      appBar: AppBar(title: Text(current.displayName)),
      body: ListView(
        children: [
          PlayerCardSheet(card: current, resolveMedia: api.resolveMedia),
        ],
      ),
    );
  }
}
