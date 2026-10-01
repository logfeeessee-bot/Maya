import 'dart:math' as math;
import 'package:flutter/material.dart';

const Color _blue = Color(0xFF4C7DFF);

class OrbPainter extends CustomPainter {
  final double t;
  final bool active;
  OrbPainter(this.t, this.active);

  Offset _pt(Offset c, double ang, double rad) =>
      Offset(c.dx + rad * math.cos(ang), c.dy + rad * math.sin(ang));

  void _star(Canvas canvas, Offset p, double s) {
    final line = Paint()
      ..color = const Color(0xCCFFFFFF)
      ..strokeWidth = 1.6
      ..strokeCap = StrokeCap.round;
    canvas.drawLine(p.translate(-s, 0), p.translate(s, 0), line);
    canvas.drawLine(p.translate(0, -s), p.translate(0, s), line);
    canvas.drawCircle(p, 2, Paint()..color = Colors.white);
  }

  Paint _stroke(double w, Color col) => Paint()
    ..style = PaintingStyle.stroke
    ..strokeWidth = w
    ..strokeCap = StrokeCap.round
    ..color = col;

  @override
  void paint(Canvas canvas, Size size) {
    final c = Offset(size.width / 2, size.height / 2);
    final full = 2 * math.pi;
    final sway = math.sin(t * full) * (active ? 0.5 : 0.12);

    // glow
    canvas.drawCircle(
        c,
        150,
        Paint()
          ..shader = const RadialGradient(
                  colors: [Color(0x224C7DFF), Color(0x00000000)])
              .createShader(Rect.fromCircle(center: c, radius: 150)));

    // corner circuit lines
    final circ = _stroke(1, const Color(0x334C7DFF));
    final dot = Paint()..color = const Color(0x664C7DFF);
    for (final sx in [1.0, -1.0]) {
      for (final sy in [1.0, -1.0]) {
        final x0 = c.dx + sx * 165;
        final y0 = c.dy + sy * 85;
        final path = Path()
          ..moveTo(x0, y0)
          ..lineTo(x0 + sx * 12, y0)
          ..lineTo(x0 + sx * 22, y0 - sy * 14)
          ..lineTo(x0 + sx * 22, y0 - sy * 40);
        canvas.drawPath(path, circ);
        canvas.drawCircle(Offset(x0 + sx * 22, y0 - sy * 40), 2, dot);
      }
    }

    // tick marks
    final tick = Paint()
      ..strokeWidth = 1
      ..color = const Color(0x33FFFFFF);
    for (int i = 0; i < 120; i++) {
      final a = i * full / 120;
      final len = i % 5 == 0 ? 7.0 : 4.0;
      canvas.drawLine(_pt(c, a, 152), _pt(c, a, 152 + len), tick);
    }

    // rings
    canvas.drawCircle(c, 150, _stroke(1.5, const Color(0x44FFFFFF)));
    canvas.drawCircle(c, 124, _stroke(26, const Color(0x1AFFFFFF)));
    canvas.drawCircle(c, 110, _stroke(1.5, const Color(0x554C7DFF)));

    final rect124 = Rect.fromCircle(center: c, radius: 124);
    final rect150 = Rect.fromCircle(center: c, radius: 150);
    final rect106 = Rect.fromCircle(center: c, radius: 106);

    // grey arc (left)
    final greyGlow = _stroke(18, const Color(0x44FFFFFF))
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 8);
    final grey = _stroke(13, const Color(0xFFC4C4CE));
    final gStart = math.pi - 0.8 + sway;
    canvas.drawArc(rect124, gStart, 1.6, false, greyGlow);
    canvas.drawArc(rect124, gStart, 1.6, false, grey);

    // blue arc (right)
    final blueGlow = _stroke(10, const Color(0x664C7DFF))
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6);
    final bStart = -0.7 - sway;
    canvas.drawArc(rect124, bStart, 1.5, false, blueGlow);
    canvas.drawArc(rect124, bStart, 1.5, false, _stroke(5, _blue));

    // outer small blue arc (right)
    canvas.drawArc(rect150, -0.45 + sway, 0.55, false, _stroke(4, _blue));

    // inner thin blue arc (left)
    canvas.drawArc(rect106, math.pi - 0.6, 1.2, false,
        _stroke(4, const Color(0xAA4C7DFF)));

    // sparkle crosses
    _star(canvas, _pt(c, -2.2, 156), 12);
    _star(canvas, _pt(c, -0.95, 140), 13);
    _star(canvas, _pt(c, 0.0, 128), 18);
    _star(canvas, _pt(c, 2.0, 150), 11);
    _star(canvas, _pt(c, math.pi / 2 + 0.2, 160), 11);
    _star(canvas, _pt(c, -math.pi / 2 - 0.1, 162), 11);

    // equalizer bars
    final by = c.dy + 24;
    for (int i = 0; i < 41; i++) {
      final x = c.dx - 100 + i * 5.0;
      final env = 1 - ((i - 20).abs() / 20);
      final h = active
          ? 3 + 16 * env * (0.5 + 0.5 * math.sin(t * full * 6 + i * 0.7))
          : 2 + 4 * env;
      final alpha = (90 + 130 * env).round().clamp(0, 255);
      canvas.drawLine(
          Offset(x, by - h / 2),
          Offset(x, by + h / 2),
          Paint()
            ..strokeWidth = 2
            ..strokeCap = StrokeCap.round
            ..color = Color.fromARGB(alpha, 108, 140, 255));
    }

    // rainbow rings
    if (active) {
      const cols = [
        Color(0xFFFF4D9D),
        Color(0xFFA64DFF),
        Color(0xFF4C7DFF),
        Color(0xFF00E5FF),
        Color(0xFF00E676),
        Color(0xFFFFEA00),
        Color(0xFFFF4D9D),
      ];
      final a = t * full;
      for (int i = 0; i < 4; i++) {
        final p = (t * 3 + i / 4) % 1.0;
        final rad = 60 + p * 85;
        final rect = Rect.fromCircle(center: c, radius: rad);
        final paint = Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 6 * (1 - p) + 1
          ..shader = SweepGradient(
            colors: cols,
            transform: GradientRotation(a * (i.isEven ? 1 : -1)),
          ).createShader(rect);
        canvas.drawCircle(c, rad, paint);
      }
    }
  }

  @override
  bool shouldRepaint(covariant OrbPainter old) => true;
}
