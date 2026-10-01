import 'dart:math' as math;
import 'package:flutter/material.dart';

void main() => runApp(const MayaApp());

const kBg = Color(0xFF0B1220);
const kCard = Color(0xFF151D2E);
const kBlue = Color(0xFF4C7DFF);

class MayaApp extends StatelessWidget {
  const MayaApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(useMaterial3: true)
          .copyWith(scaffoldBackgroundColor: kBg),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctl;
  bool listening = false;

  @override
  void initState() {
    super.initState();
    _ctl = AnimationController(
        vsync: this, duration: const Duration(seconds: 6))
      ..repeat();
  }

  @override
  void dispose() {
    _ctl.dispose();
    super.dispose();
  }

  Widget chip(IconData i, String t) => Expanded(
        child: Container(
          height: 56,
          margin: const EdgeInsets.symmetric(horizontal: 5),
          decoration: BoxDecoration(
              color: kCard, borderRadius: BorderRadius.circular(18)),
          child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            Icon(i, size: 20, color: Colors.white70),
            const SizedBox(width: 8),
            Text(t,
                style: const TextStyle(
                    fontSize: 16, fontWeight: FontWeight.w600)),
          ]),
        ),
      );

  Widget info(IconData i, String a, String b, String c) => Expanded(
        child: Container(
          height: 100,
          margin: const EdgeInsets.symmetric(horizontal: 5),
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
              color: kCard,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: Colors.white12)),
          child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(children: [
                  Icon(i, size: 14, color: Colors.white54),
                  const SizedBox(width: 6),
                  Text(a,
                      style: const TextStyle(
                          fontSize: 13, color: Colors.white54)),
                ]),
                Text(b,
                    style: const TextStyle(
                        fontSize: 24, fontWeight: FontWeight.bold)),
                Text(c,
                    style: const TextStyle(
                        fontSize: 13, color: Colors.white54)),
              ]),
        ),
      );

  Widget navItem(IconData i, String t, bool on) => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(i, color: on ? kBlue : Colors.white54),
          Text(t,
              style: TextStyle(
                  fontSize: 12, color: on ? kBlue : Colors.white54)),
        ],
      );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButtonLocation: FloatingActionButtonLocation.centerDocked,
      floatingActionButton: SizedBox(
        width: 68,
        height: 68,
        child: FloatingActionButton(
          backgroundColor: kBlue,
          shape: const CircleBorder(),
          onPressed: () => setState(() => listening = !listening),
          child: Icon(listening ? Icons.stop : Icons.mic,
              size: 32, color: Colors.white),
        ),
      ),
      bottomNavigationBar: BottomAppBar(
        color: const Color(0xFF0F1626),
        shape: const CircularNotchedRectangle(),
        notchMargin: 8,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            navItem(Icons.home, 'Home', true),
            navItem(Icons.center_focus_strong, 'Scan', false),
            const SizedBox(width: 60),
            navItem(Icons.psychology, 'Memories', false),
            navItem(Icons.chat, 'Chat', false),
          ],
        ),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Column(children: [
            const SizedBox(height: 8),
            const Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                SizedBox(width: 40),
                Text('Maya',
                    style:
                        TextStyle(fontSize: 26, fontWeight: FontWeight.bold)),
                Icon(Icons.notifications_none, size: 28),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                  color: const Color(0xFF141C33),
                  borderRadius: BorderRadius.circular(20)),
              child: const Row(children: [
                Icon(Icons.lock, color: kBlue),
                SizedBox(width: 12),
                Expanded(
                  child: Text(
                      'Free mode • 10:00 min left today\nActivate a license for tools and unlimited talk',
                      style: TextStyle(fontSize: 13)),
                ),
                Text('Activate',
                    style: TextStyle(
                        color: kBlue,
                        fontSize: 18,
                        fontWeight: FontWeight.bold)),
              ]),
            ),
            const SizedBox(height: 14),
            const Align(
              alignment: Alignment.centerLeft,
              child: Text('Good morning,',
                  style: TextStyle(fontSize: 24, color: Colors.white60)),
            ),
            const Align(
              alignment: Alignment.centerLeft,
              child: Text('there',
                  style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold)),
            ),
            Expanded(
              child: Center(
                child: AnimatedBuilder(
                  animation: _ctl,
                  builder: (context, _) => SizedBox(
                    width: 320,
                    height: 320,
                    child: CustomPaint(
                      painter: RingPainter(_ctl.value, listening),
                      child: Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Text('AI ASSISTANT',
                                style: TextStyle(
                                    fontSize: 11,
                                    letterSpacing: 2,
                                    color: Colors.white60)),
                            ShaderMask(
                              shaderCallback: (r) => const LinearGradient(
                                      colors: [
                                    Colors.white,
                                    Color(0xFF6C8CFF),
                                    Color(0xFFE066C8)
                                  ]).createShader(r),
                              child: const Text('M.A.Y.A',
                                  style: TextStyle(
                                      fontSize: 52,
                                      fontWeight: FontWeight.bold,
                                      color: Colors.white)),
                            ),
                            Text(
                                listening
                                    ? 'LISTENING...'
                                    : 'HOW CAN I HELP YOU?',
                                style: const TextStyle(
                                    fontSize: 11,
                                    letterSpacing: 1.5,
                                    color: Colors.white54)),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
            Row(children: [
              chip(Icons.music_note, 'Music'),
              chip(Icons.menu_book, 'Study'),
              chip(Icons.edit, 'Journal'),
            ]),
            const SizedBox(height: 10),
            Row(children: [
              info(Icons.cloud, 'Weather', '—', 'No data'),
              info(Icons.calendar_today, 'Today', '30', 'Wed, Sep'),
              info(Icons.favorite, 'Mood', 'Warm', 'All good'),
            ]),
            const SizedBox(height: 10),
            Container(
              height: 54,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              decoration: BoxDecoration(
                  color: kCard,
                  borderRadius: BorderRadius.circular(30),
                  border: Border.all(color: Colors.white12)),
              child: const Row(children: [
                Icon(Icons.attach_file, color: Colors.white54),
                SizedBox(width: 12),
                Expanded(
                    child: Text('Ask Maya anything...',
                        style:
                            TextStyle(fontSize: 18, color: Colors.white38))),
                Icon(Icons.send, color: Colors.white54),
              ]),
            ),
            const SizedBox(height: 10),
          ]),
        ),
      ),
    );
  }
}

class RingPainter extends CustomPainter {
  final double t;
  final bool listening;
  RingPainter(this.t, this.listening);

  @override
  void paint(Canvas canvas, Size size) {
    final c = Offset(size.width / 2, size.height / 2);
    final r = size.width / 2 - 20;
    final base = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3
      ..color = const Color(0x334C7DFF);
    canvas.drawCircle(c, r, base);
    canvas.drawCircle(c, r - 18, base);

    final a = t * 2 * math.pi;
    final grey = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 10
      ..strokeCap = StrokeCap.round
      ..color = const Color(0xFFBDBDC8);
    canvas.drawArc(Rect.fromCircle(center: c, radius: r), a, 1.2, false, grey);
    final blue = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 6
      ..strokeCap = StrokeCap.round
      ..color = kBlue;
    canvas.drawArc(
        Rect.fromCircle(center: c, radius: r - 18), -a * 1.5, 1.0, false, blue);

    if (listening) {
      const cols = [
        Color(0xFFFF4D9D),
        Color(0xFFA64DFF),
        Color(0xFF4C7DFF),
        Color(0xFF00E5FF),
        Color(0xFF00E676),
        Color(0xFFFFEA00),
        Color(0xFFFF4D9D),
      ];
      for (int i = 0; i < 4; i++) {
        final p = (t * 3 + i / 4) % 1.0;
        final rad = r * 0.55 + p * r * 0.6;
        final rect = Rect.fromCircle(center: c, radius: rad);
        final paint = Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 7 * (1 - p) + 1
          ..shader = SweepGradient(
            colors: cols,
            transform: GradientRotation(a * (i.isEven ? 1 : -1)),
          ).createShader(rect);
        canvas.drawCircle(c, rad, paint);
      }
    }
  }

  @override
  bool shouldRepaint(covariant RingPainter old) => true;
}
