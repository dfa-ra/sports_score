import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/api_client.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/theme.dart';
import '../../state/auth_controller.dart';
import '../../state/league_store.dart';

enum _Kind { goal, yellow, red }

class RefereePadPage extends StatefulWidget {
  const RefereePadPage({super.key, required this.matchId});
  final String matchId;

  @override
  State<RefereePadPage> createState() => _RefereePadPageState();
}

class _RefereePadPageState extends State<RefereePadPage> {
  LeagueMatch? match;
  List<MatchEvent> events = [];
  List<TeamMember> homeRoster = [];
  List<TeamMember> awayRoster = [];
  bool loading = true;
  bool pending = false;
  String? error;
  String? ok;
  Timer? _tick;
  DateTime now = DateTime.now();

  @override
  void initState() {
    super.initState();
    _tick = Timer.periodic(const Duration(milliseconds: 250), (_) {
      if (!mounted) return;
      if (match?.status == 'LIVE') setState(() => now = DateTime.now());
    });
    _load();
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    final api = context.read<AuthController>().api;
    final store = context.read<LeagueStore>();
    try {
      final data = await api.get('/matches/${widget.matchId}');
      final ev = await api.get('/matches/${widget.matchId}/events');
      if (data is! Map) return;
      final next = LeagueMatch.fromJson(Map<String, dynamic>.from(data));
      final home = await store.teamMembers(next.homeTeamId);
      final away = await store.teamMembers(next.awayTeamId);
      if (!mounted) return;
      setState(() {
        match = next;
        events = ((ev as List?) ?? const [])
            .whereType<Map>()
            .map((item) => MatchEvent.fromJson(Map<String, dynamic>.from(item)))
            .toList();
        homeRoster = home;
        awayRoster = away;
      });
    } catch (e) {
      if (mounted) setState(() => error = e is ApiException ? e.message : 'Матч не загрузился.');
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> _action(String path) async {
    setState(() {
      pending = true;
      error = null;
      ok = null;
    });
    try {
      await context.read<AuthController>().api.post('/referee/matches/${widget.matchId}/$path');
      await _load();
      if (mounted) setState(() => ok = path == 'finish' ? 'Матч завершён.' : 'Готово.');
    } catch (e) {
      if (mounted) setState(() => error = e is ApiException ? e.message : 'Действие не прошло.');
    } finally {
      if (mounted) setState(() => pending = false);
    }
  }

  Future<void> _addEvent(Map<String, dynamic> body) async {
    setState(() {
      pending = true;
      error = null;
      ok = null;
    });
    try {
      await context.read<AuthController>().api.post('/referee/matches/${widget.matchId}/events', body);
      await _load();
      if (mounted) setState(() => ok = 'В протоколе.');
    } catch (e) {
      if (mounted) setState(() => error = e is ApiException ? e.message : 'Не удалось записать событие');
    } finally {
      if (mounted) setState(() => pending = false);
    }
  }

  Future<void> _openSheet(_Kind kind) async {
    final current = match;
    if (current == null) return;
    if (!current.isLive) {
      setState(() => error = 'Сначала стартуйте матч.');
      return;
    }
    final store = context.read<LeagueStore>();
    final teamId = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (context) => _SheetFrame(
        title: kind == _Kind.goal ? 'Кто забил?' : kind == _Kind.yellow ? 'Кому жёлтая?' : 'Кому красная?',
        hint: 'Сначала команда.',
        step: '1 / ${kind == _Kind.goal ? 3 : 2}',
        child: Column(
          children: [
            _Pick(label: store.teamName(current.homeTeamId), onTap: () => Navigator.pop(context, current.homeTeamId)),
            _Pick(label: store.teamName(current.awayTeamId), onTap: () => Navigator.pop(context, current.awayTeamId)),
          ],
        ),
      ),
    );
    if (!mounted || teamId == null) return;

    final roster = teamId == current.homeTeamId ? homeRoster : awayRoster;
    final player = await showModalBottomSheet<TeamMember>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (context) => _SheetFrame(
        title: kind == _Kind.goal ? 'Кто забил гол?' : kind == _Kind.yellow ? 'Кому показать жёлтую?' : 'Кому показать красную?',
        hint: 'Выберите игрока из заявки.',
        step: '2 / ${kind == _Kind.goal ? 3 : 2}',
        child: roster.isEmpty
            ? const Text('В заявке никого.', style: TextStyle(color: AppColors.muted))
            : Column(
                children: [
                  for (final p in roster)
                    _Pick(label: playerTag(p.displayName, p.jerseyNumber), onTap: () => Navigator.pop(context, p)),
                ],
              ),
      ),
    );
    if (!mounted || player == null) return;

    if (kind != _Kind.goal) {
      await _addEvent({
        'eventType': kind == _Kind.yellow ? 'YELLOW_CARD' : 'RED_CARD',
        'teamId': teamId,
        'playerId': player.playerId,
      });
      return;
    }

    final others = roster.where((p) => p.playerId != player.playerId).toList();
    final assist = await showModalBottomSheet<String?>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (context) => _SheetFrame(
        title: 'Кто отдал передачу?',
        hint: 'Гол: ${playerTag(player.displayName, player.jerseyNumber)}. Если паса не было — пропустите.',
        step: '3 / 3',
        child: Column(
          children: [
            _Pick(label: 'Без передачи', filled: true, onTap: () => Navigator.pop(context, '')),
            for (final p in others)
              _Pick(label: playerTag(p.displayName, p.jerseyNumber), onTap: () => Navigator.pop(context, p.playerId)),
          ],
        ),
      ),
    );
    if (!mounted || assist == null) return;
    await _addEvent({
      'eventType': 'GOAL',
      'teamId': teamId,
      'playerId': player.playerId,
      if (assist.isNotEmpty) 'secondaryPlayerId': assist,
    });
  }

  @override
  Widget build(BuildContext context) {
    if (loading && match == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator(color: AppColors.ice)));
    }
    final current = match;
    if (current == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Пульт')),
        body: Center(child: Text(error ?? 'Матч не найден')),
      );
    }
    final store = context.watch<LeagueStore>();
    final home = store.teamName(current.homeTeamId);
    final away = store.teamName(current.awayTeamId);
    final cap = current.periodLengthSeconds;
    final elapsed = matchElapsedSeconds(
      status: current.status,
      gameTimeSeconds: current.gameTimeSeconds ?? 0,
      clockRunningSince: current.clockRunningSince,
      cap: cap,
      now: now,
    );
    final remaining = cap - elapsed;
    final live = current.isLive;
    final protocol = events.reversed.toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Пульт'),
        leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: () => context.go('/referee')),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: current.status == 'LIVE' ? AppColors.ice : AppColors.line),
            ),
            child: Column(
              children: [
                Text(
                  matchStateLabel(current.status).toUpperCase(),
                  style: TextStyle(color: live ? AppColors.ice : AppColors.navy, fontWeight: FontWeight.w800, letterSpacing: 0.6),
                ),
                const SizedBox(height: 6),
                Text(periodLabel(current.period, sportCode: current.sportCode, periodCount: current.periodCount), style: const TextStyle(color: AppColors.muted, fontWeight: FontWeight.w700)),
                Text(formatClock(remaining), style: TextStyle(fontSize: 48, fontWeight: FontWeight.w800, color: remaining <= 0 && live ? AppColors.danger : AppColors.navy)),
                Text('осталось из ${formatClock(cap)} · прошло ${formatClock(elapsed)}', style: const TextStyle(color: AppColors.muted, fontSize: 12)),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(child: Text(home, textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy))),
                    Text('${current.homeScore} : ${current.awayScore}', style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: AppColors.navy)),
                    Expanded(child: Text(away, textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy))),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _Ctl(label: 'Старт', onTap: pending || current.status != 'SCHEDULED' ? null : () => _action('start')),
              _Ctl(label: 'Пауза', onTap: pending || current.status != 'LIVE' ? null : () => _action('pause')),
              _Ctl(label: 'Продолжить', onTap: pending || current.status != 'PAUSED' ? null : () => _action('resume')),
              _Ctl(
                label: current.sportCode == 'BASKETBALL' ? 'Четверть' : 'Тайм',
                onTap: pending || !live || (current.period ?? 1) >= current.periodCount ? null : () => _action('next-period'),
              ),
              _Ctl(label: 'Финиш', danger: true, onTap: pending || !live ? null : () => _action('finish')),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(child: _Big(label: 'Гол', color: AppColors.navy, onTap: pending ? null : () => _openSheet(_Kind.goal))),
              const SizedBox(width: 8),
              Expanded(child: _Big(label: 'Жёлтая', color: const Color(0xFFC47B00), onTap: pending ? null : () => _openSheet(_Kind.yellow))),
              const SizedBox(width: 8),
              Expanded(child: _Big(label: 'Красная', color: AppColors.danger, onTap: pending ? null : () => _openSheet(_Kind.red))),
            ],
          ),
          if (error != null) ...[
            const SizedBox(height: 10),
            Text(error!, style: const TextStyle(color: AppColors.danger)),
          ],
          if (ok != null) ...[
            const SizedBox(height: 10),
            Text(ok!, style: const TextStyle(color: AppColors.win)),
          ],
          const SizedBox(height: 18),
          const Text('Протокол', style: TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy, fontSize: 16)),
          const SizedBox(height: 8),
          if (protocol.isEmpty)
            const Text('Пока тихо.', style: TextStyle(color: AppColors.muted))
          else
            for (final ev in protocol)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Row(
                  children: [
                    SizedBox(width: 48, child: Text(formatClock(ev.gameTime), style: const TextStyle(color: AppColors.ice, fontWeight: FontWeight.w700))),
                    Expanded(child: Text(ev.label, style: const TextStyle(fontWeight: FontWeight.w700))),
                  ],
                ),
              ),
        ],
      ),
    );
  }
}

class _SheetFrame extends StatelessWidget {
  const _SheetFrame({required this.title, required this.hint, required this.step, required this.child});
  final String title;
  final String hint;
  final String step;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(step, style: const TextStyle(color: AppColors.ice, fontWeight: FontWeight.w800, letterSpacing: 0.6, fontSize: 12)),
            const SizedBox(height: 4),
            Text(title, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: AppColors.navy)),
            const SizedBox(height: 4),
            Text(hint, style: const TextStyle(color: AppColors.muted)),
            const SizedBox(height: 12),
            ConstrainedBox(
              constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.5),
              child: SingleChildScrollView(child: child),
            ),
          ],
        ),
      ),
    );
  }
}

class _Pick extends StatelessWidget {
  const _Pick({required this.label, required this.onTap, this.filled = false});
  final String label;
  final VoidCallback onTap;
  final bool filled;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: SizedBox(
        width: double.infinity,
        child: filled
            ? FilledButton(onPressed: onTap, child: Text(label))
            : OutlinedButton(onPressed: onTap, child: Text(label)),
      ),
    );
  }
}

class _Ctl extends StatelessWidget {
  const _Ctl({required this.label, this.onTap, this.danger = false});
  final String label;
  final VoidCallback? onTap;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton(
      onPressed: onTap,
      style: OutlinedButton.styleFrom(foregroundColor: danger ? AppColors.danger : AppColors.navy),
      child: Text(label),
    );
  }
}

class _Big extends StatelessWidget {
  const _Big({required this.label, required this.color, this.onTap});
  final String label;
  final Color color;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return ElevatedButton(
      onPressed: onTap,
      style: ElevatedButton.styleFrom(
        minimumSize: const Size.fromHeight(72),
        backgroundColor: color,
        foregroundColor: Colors.white,
      ),
      child: Text(label, style: const TextStyle(fontWeight: FontWeight.w800)),
    );
  }
}
