import 'dart:math' as math;
import 'package:flutter/material.dart';

final ValueNotifier<bool> glowActive = ValueNotifier<bool>(false);

class EdgeGlowOverlay extends StatefulWidget {
  const EdgeGlowOverlay({super.key});
  @override
  State<EdgeGlowOverlay> createState() => _EdgeGlowOverlayState();
}

class _EdgeGlowOverlayState extends State<EdgeGlowOverlay>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c;

  @override
  void initState() {
    super.initState();
    _c = AnimationController(
        vsync: this, duration: const Duration(seconds: 3))
      ..repeat();
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: ValueListenableBuilder<bool>(
        valueListenable: glowActive,
        builder: (context, on, _) => AnimatedOpacity(
          opacity: on ? 1.0 : 0.0,
          duration: const Duration(milliseconds: 400),
          child: AnimatedBuilder(
            animation: _c,
            builder: (context, _) => CustomPaint(
              size: Size.infinite,
              painter: _GlowPainter(_c.value),
            ),
          ),
        ),
      ),
    );
  }
}

class _GlowPainter extends CustomPainter {
  final double t;
  _GlowPainter(this.t);

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final rr = RRect.fromRectAndRadius(
        rect.deflate(3), const Radius.circular(36));
    const cols = [
      Color(0xFFFF4D9D),
      Color(0xFFA64DFF),
      Color(0xFF4C7DFF),
      Color(0xFF00E5FF),
      Color(0xFF00E676),
      Color(0xFFFFEA00),
      Color(0xFFFF4D9D),
    ];
    final shader = SweepGradient(
      colors: cols,
      transform: GradientRotation(t * 2 * math.pi),
    ).createShader(rect);

    Paint p(double w, double blur) {
      final paint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = w
        ..shader = shader;
      if (blur > 0) {
        paint.maskFilter = MaskFilter.blur(BlurStyle.normal, blur);
      }
      return paint;
    }

    canvas.drawRRect(rr, p(28, 22));
    canvas.drawRRect(rr, p(10, 8));
    canvas.drawRRect(rr, p(3, 0));
  }

  @override
  bool shouldRepaint(covariant _GlowPainter old) => true;
}
