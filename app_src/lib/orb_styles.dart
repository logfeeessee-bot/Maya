import 'dart:async';
import 'dart:math' as math;
import 'package:battery_plus/battery_plus.dart';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

const List<String> orbNames = [
  'Classic',
  'Rainbow',
  'Neon',
  'Green',
  'Plasma',
  'Fire',
  'Cyan',
  'Sphere',
];

ValueNotifier<int> _makeStyle() {
  final n = ValueNotifier<int>(0);
  SharedPreferences.getInstance().then((p) {
    n.value = p.getInt('orbStyle') ?? 0;
  });
  return n;
}

ValueNotifier<int> _makeBattery() {
  final n = ValueNotifier<int>(-1);
  Future<void> upd() async {
    try {
      n.value = await Battery().batteryLevel;
    } catch (_) {}
  }

  upd();
  Timer.periodic(const Duration(seconds: 30), (_) => upd());
  return n;
}

final ValueNotifier<int> orbStyle = _makeStyle();
final ValueNotifier<int> batteryLevel = _makeBattery();

class StylePicker extends StatelessWidget {
  const StylePicker({super.key});
  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<int>(
      valueListenable: orbStyle,
      builder: (context, cur, _) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Orb style',
              style: TextStyle(fontSize: 13, color: Colors.white70)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (int i = 0; i < orbNames.length; i++)
                ChoiceChip(
                  label: Text(orbNames[i]),
                  selected: cur == i,
                  onSelected: (_) async {
                    orbStyle.value = i;
                    final p = await SharedPreferences.getInstance();
                    await p.setInt('orbStyle', i);
                  },
                ),
            ],
          ),
        ],
      ),
    );
  }
}

Paint _p(double w,
    {Color col = Colors.white, Shader? sh, double blur = 0}) {
  final p = Paint()
    ..style = PaintingStyle.stroke
    ..strokeWidth = w
    ..strokeCap = StrokeCap.round
    ..color = col;
  if (sh != null) {
    p.shader = sh;
  }
  if (blur > 0) {
    p.maskFilter = MaskFilter.blur(BlurStyle.normal, blur);
  }
  return p;
}

Shader _sweep(Rect r, List<Color> cols, double rot) {
  return SweepGradient(colors: cols, transform: GradientRotation(rot))
      .createShader(r);
}

void paintStyle(
    Canvas canvas, Size size, int style, double t, bool active) {
  final c = Offset(size.width / 2, size.height / 2);
  final pulse = active ? 1.0 + 0.04 * math.sin(t * 2 * math.pi * 6) : 1.0;
  switch (style) {
    case 1:
      _rainbow(canvas, c, t, pulse);
      break;
    case 2:
      _neon(canvas, c, t, pulse);
      break;
    case 3:
      _dotsRing(canvas, c, t, pulse);
      break;
    case 4:
      _plasma(canvas, c, t, pulse);
      break;
    case 5:
      _fire(canvas, c, t, pulse);
      break;
    case 6:
      _cyan(canvas, c, t, pulse);
      break;
    case 7:
      _sphere(canvas, c, t, pulse);
      break;
    default:
      break;
  }
  _percent(canvas, c);
}

void _percent(Canvas canvas, Offset c) {
  final lv = batteryLevel.value;
  if (lv < 0) {
    return;
  }
  final tp = TextPainter(
    text: TextSpan(
      text: '$lv%',
      style: const TextStyle(
          color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold),
    ),
    textDirection: TextDirection.ltr,
  )..layout();
  tp.paint(canvas, Offset(c.dx - tp.width / 2, c.dy + 56));
}

void _rainbow(Canvas canvas, Offset c, double t, double pulse) {
  const cols = [
    Color(0xFFFF2D55),
    Color(0xFFFFEA00),
    Color(0xFF00E676),
    Color(0xFF00E5FF),
    Color(0xFF4C7DFF),
    Color(0xFFA64DFF),
    Color(0xFFFF2D55),
  ];
  final r = 104.0 * pulse;
  final rect = Rect.fromCircle(center: c, radius: r);
  final sh = _sweep(rect, cols, t * 2 * math.pi);
  canvas.drawCircle(c, r, _p(26, sh: sh, blur: 18));
  canvas.drawCircle(c, r, _p(12, sh: sh, blur: 4));
  canvas.drawCircle(c, r, _p(5, sh: sh));
  for (int i = 0; i < 40; i++) {
    final a = i * 2 * math.pi / 40 + t * 2 * math.pi;
    final len =
        8 + 14 * (0.5 + 0.5 * math.sin(i * 1.7 + t * 2 * math.pi * 3));
    final p1 = Offset(
        c.dx + (r + 8) * math.cos(a), c.dy + (r + 8) * math.sin(a));
    final p2 = Offset(c.dx + (r + 8 + len) * math.cos(a),
        c.dy + (r + 8 + len) * math.sin(a));
    canvas.drawLine(p1, p2, _p(2, sh: sh));
  }
}

void _neon(Canvas canvas, Offset c, double t, double pulse) {
  const a = [
    Color(0xFF4C7DFF),
    Color(0xFFA64DFF),
    Color(0xFFFF4D9D),
    Color(0xFFA64DFF),
    Color(0xFF4C7DFF),
  ];
  const b = [
    Color(0xFF00E5FF),
    Color(0xFF4C7DFF),
    Color(0xFFA64DFF),
    Color(0xFF4C7DFF),
    Color(0xFF00E5FF),
  ];
  final r1 = 106.0 * pulse;
  final r2 = 124.0 * pulse;
  final s1 = _sweep(Rect.fromCircle(center: c, radius: r1), a,
      t * 2 * math.pi);
  final s2 = _sweep(Rect.fromCircle(center: c, radius: r2), b,
      -t * 2 * math.pi);
  canvas.drawCircle(c, r1, _p(18, sh: s1, blur: 14));
  canvas.drawCircle(c, r1, _p(7, sh: s1));
  canvas.drawCircle(c, r2, _p(10, sh: s2, blur: 10));
  canvas.drawCircle(c, r2, _p(3, sh: s2));
}

void _dotsRing(Canvas canvas, Offset c, double t, double pulse) {
  canvas.drawCircle(
      c, 106.0 * pulse, _p(18, col: const Color(0x3300E676), blur: 14));
  final paint = Paint();
  for (int i = 0; i < 260; i++) {
    final a = i * 2 * math.pi / 260 + t * 2 * math.pi * 0.5;
    final rnd = (math.sin(i * 12.9898) * 43758.5453).abs() % 1.0;
    final rad = 98.0 * pulse +
        rnd * 16 +
        4 * math.sin(t * 2 * math.pi * 3 + i);
    final alpha = (110 + 145 * rnd).round();
    paint.color = Color.fromARGB(alpha, 57, 255, 106);
    canvas.drawCircle(
        Offset(c.dx + rad * math.cos(a), c.dy + rad * math.sin(a)),
        1.0 + rnd * 1.8,
        paint);
  }
}

void _bolts(Canvas canvas, Offset c, double t, double r, int n,
    Color glow, Color core) {
  for (int k = 0; k < n; k++) {
    final a = k * 2 * math.pi / n + t * 2 * math.pi * 0.2;
    final path = Path();
    for (int s = 0; s <= 4; s++) {
      final rad = r - 22 + s * 9.0;
      final jit = 7 * math.sin(k * 3.1 + s * 2.3 + t * 2 * math.pi * 5);
      final ang = a + jit / rad;
      final pt = Offset(
          c.dx + rad * math.cos(ang), c.dy + rad * math.sin(ang));
      if (s == 0) {
        path.moveTo(pt.dx, pt.dy);
      } else {
        path.lineTo(pt.dx, pt.dy);
      }
    }
    canvas.drawPath(path, _p(5, col: glow, blur: 5));
    canvas.drawPath(path, _p(1.6, col: core));
  }
}

void _plasma(Canvas canvas, Offset c, double t, double pulse) {
  final r = 112.0 * pulse;
  canvas.drawCircle(
      c,
      r,
      Paint()
        ..shader = const RadialGradient(colors: [
          Color(0x00A64DFF),
          Color(0x44A64DFF),
          Color(0x00FF4DD2),
        ]).createShader(Rect.fromCircle(center: c, radius: r + 20)));
  canvas.drawCircle(c, r, _p(3, col: const Color(0xFFFF4DD2), blur: 3));
  _bolts(canvas, c, t, r, 18, const Color(0x66FF4DD2),
      const Color(0xFFFFD6F6));
}

void _fire(Canvas canvas, Offset c, double t, double pulse) {
  const cols = [
    Color(0xFFFF3D00),
    Color(0xFFFFB300),
    Color(0xFFFFEA00),
    Color(0xFFFF6D00),
    Color(0xFFFF3D00),
  ];
  final r = 108.0 * pulse;
  final sh = _sweep(Rect.fromCircle(center: c, radius: r), cols,
      t * 2 * math.pi);
  canvas.drawCircle(c, r, _p(20, sh: sh, blur: 14));
  canvas.drawCircle(c, r, _p(6, sh: sh));
  _bolts(canvas, c, t, r + 6, 14, const Color(0x66FF6D00),
      const Color(0xFFFFF3C4));
  for (int i = 0; i < 3; i++) {
    final a = t * 2 * math.pi * (i + 1) * 0.6 + i * 2.1;
    canvas.drawCircle(
        Offset(c.dx + r * math.cos(a), c.dy + r * math.sin(a)),
        5,
        Paint()..color = const Color(0xFFFFF3C4));
  }
}

void _cyan(Canvas canvas, Offset c, double t, double pulse) {
  const cols = [
    Color(0xFF00E5FF),
    Color(0xFF4C7DFF),
    Color(0xFFFF4DD2),
    Color(0xFF00E5FF),
  ];
  final r = 108.0 * pulse;
  final sh = _sweep(Rect.fromCircle(center: c, radius: r), cols,
      t * 2 * math.pi);
  canvas.drawOval(
      Rect.fromCenter(
          center: Offset(c.dx, c.dy + r + 14), width: 190, height: 22),
      Paint()
        ..color = const Color(0x66FF4DD2)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 12));
  canvas.drawCircle(c, r, _p(16, sh: sh, blur: 12));
  canvas.drawCircle(c, r, _p(6, sh: sh));
}

void _sphere(Canvas canvas, Offset c, double t, double pulse) {
  const n = 420;
  final rot = t * 2 * math.pi;
  final r = 105.0 * pulse;
  final paint = Paint();
  for (int i = 0; i < n; i++) {
    final y = 1 - (i + 0.5) * 2 / n;
    final rr = math.sqrt(1 - y * y);
    final th = i * 2.399963 + rot;
    final x = math.cos(th) * rr;
    final z = math.sin(th) * rr;
    final depth = (z + 1) / 2;
    paint.color = Color.fromARGB((60 + 195 * depth).round(), 57, 255, 106);
    canvas.drawCircle(Offset(c.dx + x * r, c.dy + y * r),
        0.8 + 1.8 * depth, paint);
  }
}
