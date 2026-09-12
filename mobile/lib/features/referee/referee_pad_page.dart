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
import '../../widgets/marks.dart';

enum _Kind { goal, yellow, red }

enum _Step { team, player, assist }

class _Sheet {
  _Sheet({required this.kind, this.step = _Step.team, this.teamId, this.player});

  final _Kind kind;
  final _Step step;
  final String? teamId;
  final TeamMember? player;

  int get totalSteps => kind == _Kind.goal ? 3 : 2;
  int get stepIndex => step == _Step.team ? 1 : step == _Step.player ? 2 : 3;

  String get title {
    if (kind == _Kind.goal) {
      if (step == _Step.team) return 'Кто забил?';
      if (step == _Step.player) return 'Кто забил гол?';
      return 'Кто отдал передачу?';
    }
    if (kind == _Kind.yellow) {
      return step == _Step.team ? 'Кому жёлтая?' : 'Кому показать жёлтую?';
    }
    return step == _Step.team ? 'Кому красная?' : 'Кому показать красную?';
  }

  String hint({required bool held, required String scorer}) {
    if (kind == _Kind.goal && step == _Step.assist) {
      return 'Гол: $scorer. Если паса не было — пропустите.';
    }
    if (step == _Step.team) return held ? 'Часы стоят. Сначала команда.' : 'Сначала команда.';
    return held ? 'Часы стоят. Выберите игрока из заявки.' : 'Выберите игрока из заявки.';
  }
}

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
  bool heldClock = false;
  ApiClient? _api;
  _Sheet? sheet;

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
    if (heldClock) {
      heldClock = false;
      _api?.post('/referee/matches/${widget.matchId}/resume').catchError((_) => null);
    }
    super.dispose();
  }

  List<TeamMember> _rosterOf(String? teamId) {
    final current = match;
    if (current == null || teamId == null) return const [];
    return teamId == current.homeTeamId ? homeRoster : awayRoster;
  }

  Future<void> _load() async {
    final api = context.read<AuthController>().api;
    _api = api;
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

  Future<void> _refreshMatch() async {
    final data = await context.read<AuthController>().api.get('/matches/${widget.matchId}');
    if (data is Map && mounted) {
      setState(() => match = LeagueMatch.fromJson(Map<String, dynamic>.from(data)));
    }
  }

  Future<void> _holdClock() async {
    if (heldClock || match?.status != 'LIVE') return;
    try {
      await context.read<AuthController>().api.post('/referee/matches/${widget.matchId}/pause');
      heldClock = true;
      await _refreshMatch();
    } catch (_) {
      heldClock = false;
    }
  }

  Future<void> _releaseClock() async {
    if (!heldClock) return;
    heldClock = false;
    try {
      if (match?.status == 'PAUSED') {
        await context.read<AuthController>().api.post('/referee/matches/${widget.matchId}/resume');
      }
      await _refreshMatch();
    } catch (_) {}
  }

  Future<void> _closeSheet() async {
    setState(() => sheet = null);
    await _releaseClock();
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
      if (mounted) {
        setState(() {
          sheet = null;
          ok = 'В протоколе.';
        });
      }
      await _releaseClock();
      await _load();
    } catch (e) {
      if (mounted) setState(() => error = e is ApiException ? e.message : 'Не удалось записать событие');
    } finally {
      if (mounted) setState(() => pending = false);
    }
  }

  Future<void> _openSheet(_Kind kind) async {
    setState(() => error = null);
    if (match == null || !match!.isLive) {
      setState(() => error = 'Сначала стартуйте матч.');
      return;
    }
    await _holdClock();
    if (!mounted) return;
    setState(() => sheet = _Sheet(kind: kind));
  }

  void _pickTeam(String teamId) {
    final current = sheet;
    if (current == null) return;
    setState(() => sheet = _Sheet(kind: current.kind, step: _Step.player, teamId: teamId));
  }

  void _pickPlayer(TeamMember player) {
    final current = sheet;
    if (current?.teamId == null) return;
    if (current!.kind == _Kind.goal) {
      setState(() => sheet = _Sheet(kind: current.kind, step: _Step.assist, teamId: current.teamId, player: player));
      return;
    }
    _addEvent({
      'eventType': current.kind == _Kind.yellow ? 'YELLOW_CARD' : 'RED_CARD',
      'teamId': current.teamId,
      'playerId': player.playerId,
    });
  }

  void _confirmGoal(String? assistPlayerId) {
    final current = sheet;
    if (current?.teamId == null || current?.player == null) return;
    _addEvent({
      'eventType': 'GOAL',
      'teamId': current!.teamId,
      'playerId': current.player!.playerId,
      if (assistPlayerId != null) 'secondaryPlayerId': assistPlayerId,
    });
  }

  void _backSheet() {
    final current = sheet;
    if (current == null) return;
    if (current.step == _Step.assist) {
      setState(() => sheet = _Sheet(kind: current.kind, step: _Step.player, teamId: current.teamId));
      return;
    }
    if (current.step == _Step.player) {
      setState(() => sheet = _Sheet(kind: current.kind));
      return;
    }
    _closeSheet();
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
    final expired = remaining <= 0 && live;
    final protocol = events.reversed.toList();
    final periodNoun = current.sportCode == 'BASKETBALL' ? 'четверть' : 'тайм';

    return Scaffold(
      appBar: AppBar(
        title: const Text('Пульт'),
        leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: () => context.go('/referee')),
      ),
      body: Stack(
        children: [
          ListView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
            children: [
              TextButton(
                onPressed: () => context.go('/referee'),
                style: TextButton.styleFrom(alignment: Alignment.centerLeft, foregroundColor: AppColors.muted, padding: EdgeInsets.zero),
                child: const Text('← К матчам', style: TextStyle(fontWeight: FontWeight.w700)),
              ),
              const Text('Пульт', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: AppColors.navy)),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 18),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: const BorderRadius.only(
                    topLeft: Radius.circular(26),
                    topRight: Radius.circular(18),
                    bottomRight: Radius.circular(22),
                    bottomLeft: Radius.circular(16),
                  ),
                  border: Border.all(color: current.status == 'LIVE' ? AppColors.ice : AppColors.line, width: current.status == 'LIVE' ? 2 : 1),
                ),
                child: Column(
                  children: [
                    Text(
                      matchStateLabel(current.status).toUpperCase(),
                      style: TextStyle(
                        color: live ? AppColors.ice : AppColors.navy,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 0.6,
                        fontSize: 12,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      periodLabel(current.period, sportCode: current.sportCode, periodCount: current.periodCount).toUpperCase(),
                      style: const TextStyle(color: AppColors.ice, fontWeight: FontWeight.w800, letterSpacing: 0.6, fontSize: 12),
                    ),
                    Text(
                      formatClock(remaining),
                      style: TextStyle(
                        fontSize: 56,
                        height: 1,
                        fontWeight: FontWeight.w800,
                        color: expired ? AppColors.danger : AppColors.ink,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'осталось из ${formatClock(cap)} · прошло ${formatClock(elapsed)}',
                      style: const TextStyle(color: AppColors.muted, fontSize: 12),
                    ),
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        Expanded(
                          child: Row(
                            children: [
                              TeamMark(name: home, logoUrl: store.teamLogo(current.homeTeamId), size: 28),
                              const SizedBox(width: 8),
                              Expanded(child: Text(home, maxLines: 2, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy))),
                            ],
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 8),
                          child: Text('${current.homeScore} : ${current.awayScore}', style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: AppColors.navy)),
                        ),
                        Expanded(
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.end,
                            children: [
                              Expanded(child: Text(away, maxLines: 2, textAlign: TextAlign.right, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy))),
                              const SizedBox(width: 8),
                              TeamMark(name: away, logoUrl: store.teamLogo(current.awayTeamId), size: 28),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              GridView.count(
                crossAxisCount: 2,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                mainAxisSpacing: 8,
                crossAxisSpacing: 8,
                childAspectRatio: 2.35,
                children: [
                  _PadButton(
                    label: 'Старт',
                    background: AppColors.ice,
                    foreground: AppColors.navy,
                    onTap: pending || current.status != 'SCHEDULED' ? null : () => _action('start'),
                  ),
                  _PadButton(
                    label: 'Пауза',
                    onTap: pending || current.status != 'LIVE' ? null : () => _action('pause'),
                  ),
                  _PadButton(
                    label: 'Продолжить',
                    onTap: pending || current.status != 'PAUSED' ? null : () => _action('resume'),
                  ),
                  _PadButton(
                    label: 'Следующий $periodNoun',
                    background: AppColors.ice,
                    foreground: AppColors.navy,
                    onTap: pending || !live || (current.period ?? 1) >= current.periodCount ? null : () => _action('next-period'),
                  ),
                  _PadButton(
                    label: 'Финиш',
                    background: AppColors.danger,
                    foreground: Colors.white,
                    onTap: pending || !live ? null : () => _action('finish'),
                  ),
                ],
              ),
              if (expired) ...[
                const SizedBox(height: 8),
                const Text('Время тайма вышло. Можно свистеть следующий или финиш.', style: TextStyle(color: AppColors.win, fontWeight: FontWeight.w700)),
              ],
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: _PadButton(
                      label: 'Гол',
                      background: AppColors.navy,
                      foreground: Colors.white,
                      tall: true,
                      onTap: pending || !live ? null : () => _openSheet(_Kind.goal),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _PadButton(
                      label: 'Жёлтая',
                      background: const Color(0xFFF5C400),
                      foreground: AppColors.navy,
                      tall: true,
                      onTap: pending || !live ? null : () => _openSheet(_Kind.yellow),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _PadButton(
                      label: 'Красная',
                      background: AppColors.danger,
                      foreground: Colors.white,
                      tall: true,
                      onTap: pending || !live ? null : () => _openSheet(_Kind.red),
                    ),
                  ),
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
              Container(
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.line),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Text('Протокол', style: TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy, fontSize: 18)),
                    const SizedBox(height: 8),
                    if (protocol.isEmpty)
                      const Text('Пока тихо.', style: TextStyle(color: AppColors.muted))
                    else
                      for (final ev in protocol)
                        Opacity(
                          opacity: ev.voided ? 0.45 : 1,
                          child: Padding(
                            padding: const EdgeInsets.symmetric(vertical: 7),
                            child: Row(
                              children: [
                                SizedBox(
                                  width: 48,
                                  child: Text(formatClock(ev.gameTime), style: const TextStyle(color: AppColors.ice, fontWeight: FontWeight.w700)),
                                ),
                                SizedBox(
                                  width: 90,
                                  child: Text(eventLabel(ev.eventType), style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
                                ),
                                Expanded(
                                  child: Text(
                                    () {
                                      final detail = eventDetail(
                                        eventType: ev.eventType,
                                        playerName: ev.playerName,
                                        playerJersey: ev.playerJersey,
                                        secondaryPlayerName: ev.secondaryPlayerName,
                                        secondaryPlayerJersey: ev.secondaryPlayerJersey,
                                      );
                                      return detail.isEmpty ? '—' : detail;
                                    }(),
                                    style: const TextStyle(color: AppColors.text),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                  ],
                ),
              ),
            ],
          ),
          if (sheet != null) _EventOverlay(
            sheet: sheet!,
            pending: pending,
            heldClock: heldClock,
            homeName: home,
            awayName: away,
            homeTeamId: current.homeTeamId,
            awayTeamId: current.awayTeamId,
            roster: _rosterOf(sheet!.teamId),
            onClose: _closeSheet,
            onBack: _backSheet,
            onPickTeam: _pickTeam,
            onPickPlayer: _pickPlayer,
            onConfirmGoal: _confirmGoal,
          ),
        ],
      ),
    );
  }
}

class _EventOverlay extends StatelessWidget {
  const _EventOverlay({
    required this.sheet,
    required this.pending,
    required this.heldClock,
    required this.homeName,
    required this.awayName,
    required this.homeTeamId,
    required this.awayTeamId,
    required this.roster,
    required this.onClose,
    required this.onBack,
    required this.onPickTeam,
    required this.onPickPlayer,
    required this.onConfirmGoal,
  });

  final _Sheet sheet;
  final bool pending;
  final bool heldClock;
  final String homeName;
  final String awayName;
  final String homeTeamId;
  final String awayTeamId;
  final List<TeamMember> roster;
  final VoidCallback onClose;
  final VoidCallback onBack;
  final ValueChanged<String> onPickTeam;
  final ValueChanged<TeamMember> onPickPlayer;
  final ValueChanged<String?> onConfirmGoal;

  @override
  Widget build(BuildContext context) {
    final others = roster.where((p) => p.playerId != sheet.player?.playerId).toList();
    final scorer = sheet.player == null ? '' : playerTag(sheet.player!.displayName, sheet.player!.jerseyNumber);
    return Material(
      color: const Color(0x6B00205B),
      child: Stack(
        children: [
          Positioned.fill(child: GestureDetector(onTap: onClose, behavior: HitTestBehavior.opaque)),
          Align(
            alignment: Alignment.bottomCenter,
            child: ConstrainedBox(
              constraints: BoxConstraints(
                maxHeight: MediaQuery.of(context).size.height * 0.82,
                maxWidth: 520,
              ),
              child: Material(
                color: Colors.white,
                borderRadius: const BorderRadius.vertical(top: Radius.circular(22)),
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(18, 16, 18, 20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        '${sheet.stepIndex} / ${sheet.totalSteps}',
                        style: const TextStyle(color: AppColors.ice, fontWeight: FontWeight.w800, letterSpacing: 0.8, fontSize: 12),
                      ),
                      const SizedBox(height: 4),
                      Text(sheet.title, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: AppColors.navy)),
                      const SizedBox(height: 4),
                      Text(sheet.hint(held: heldClock, scorer: scorer), style: const TextStyle(color: AppColors.muted)),
                      const SizedBox(height: 12),
                      if (sheet.step == _Step.team) ...[
                        _PadButton(label: homeName, onTap: pending ? null : () => onPickTeam(homeTeamId)),
                        const SizedBox(height: 8),
                        _PadButton(label: awayName, onTap: pending ? null : () => onPickTeam(awayTeamId)),
                      ] else if (sheet.step == _Step.player) ...[
                        if (roster.isEmpty)
                          const Text('В заявке никого.', style: TextStyle(color: AppColors.muted))
                        else
                          for (final player in roster) ...[
                            _PadButton(
                              label: playerTag(player.displayName, player.jerseyNumber),
                              alignStart: true,
                              onTap: pending ? null : () => onPickPlayer(player),
                            ),
                            const SizedBox(height: 8),
                          ],
                      ] else ...[
                        _PadButton(
                          label: 'Без передачи',
                          background: AppColors.ice,
                          foreground: AppColors.navy,
                          onTap: pending ? null : () => onConfirmGoal(null),
                        ),
                        const SizedBox(height: 8),
                        for (final player in others) ...[
                          _PadButton(
                            label: playerTag(player.displayName, player.jerseyNumber),
                            alignStart: true,
                            onTap: pending ? null : () => onConfirmGoal(player.playerId),
                          ),
                          const SizedBox(height: 8),
                        ],
                      ],
                      const SizedBox(height: 8),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          TextButton(onPressed: pending ? null : onBack, child: const Text('Назад')),
                          TextButton(onPressed: pending ? null : onClose, child: const Text('Закрыть')),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PadButton extends StatelessWidget {
  const _PadButton({
    required this.label,
    this.onTap,
    this.background,
    this.foreground,
    this.tall = false,
    this.alignStart = false,
  });

  final String label;
  final VoidCallback? onTap;
  final Color? background;
  final Color? foreground;
  final bool tall;
  final bool alignStart;

  @override
  Widget build(BuildContext context) {
    final fill = background;
    final text = foreground ?? (fill == null ? AppColors.navy : Colors.white);
    return SizedBox(
      height: tall ? 72 : 58,
      width: double.infinity,
      child: fill == null
          ? OutlinedButton(
              onPressed: onTap,
              style: OutlinedButton.styleFrom(
                foregroundColor: text,
                side: const BorderSide(color: AppColors.line),
                alignment: alignStart ? Alignment.centerLeft : Alignment.center,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                textStyle: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
              ),
              child: Text(label),
            )
          : FilledButton(
              onPressed: onTap,
              style: FilledButton.styleFrom(
                backgroundColor: fill,
                foregroundColor: text,
                disabledBackgroundColor: fill.withValues(alpha: 0.35),
                alignment: alignStart ? Alignment.centerLeft : Alignment.center,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                textStyle: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
              ),
              child: Text(label),
            ),
    );
  }
}
