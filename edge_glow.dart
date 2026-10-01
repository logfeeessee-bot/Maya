import 'dart:math' as math;
import 'package:flutter/material.dart';

// کون سی تھیم سلیکٹ ہے 0 سے 6 تک
final ValueNotifier<int> selectedTheme = ValueNotifier(0); 
final ValueNotifier<bool> glowActive = ValueNotifier(false);

class EdgeGlowOverlay extends StatefulWidget {
  const EdgeGlowOverlay({super.key});
  @override State<EdgeGlowOverlay> createState() => _EdgeGlowOverlayState();
}

class _EdgeGlowOverlayState extends State<EdgeGlowOverlay> with SingleTickerProviderStateMixin {
  late AnimationController _c;
  @override void initState(){ super.initState(); _c = AnimationController(vsync: this, duration: Duration(seconds: 3))..repeat(); }
  @override void dispose(){ _c.dispose(); super.dispose(); }

  @override Widget build(BuildContext context){
    return ValueListenableBuilder2(
      first: glowActive, second: selectedTheme,
      builder: (context, active, theme, _){
        if(!active) return SizedBox.shrink();
        return AnimatedBuilder(animation: _c, builder: (_, __){
          return CustomPaint(size: Size.infinite, painter: _ThemePainter(_c.value, theme));
        });
      }
    );
  }
}

// 7 تھیمز کا پینٹر
class _ThemePainter extends CustomPainter {
  final double t; final int themeIndex;
  _ThemePainter(this.t, this.themeIndex);

  @override void paint(Canvas canvas, Size size){
    final center = Offset(size.width/2, size.height/2);
    Paint paint = Paint()..style = PaintingStyle.stroke..strokeWidth = 4..maskFilter = MaskFilter.blur(BlurStyle.normal, 10);
    
    List<Color> colors;
    if(themeIndex == 0) colors = [Colors.red, Colors.orange, Colors.yellow, Colors.green, Colors.blue, Colors.purple]; // Rainbow
    else if(themeIndex == 1) colors = [Color(0xFF00F0FF), Color(0xFF7A00FF)]; // Blue Neon
    else if(themeIndex == 2) colors = [Color(0xFF00FF88), Color(0xFF00FF00)]; // Green
    else if(themeIndex == 3) colors = [Color(0xFFFF00E5), Color(0xFF9C00FF)]; // Pink Electric
    else if(themeIndex == 4) colors = [Color(0xFFFF5C00), Color(0xFFFF0000), Color(0xFFFFFF00)]; // Fire 85%
    else if(themeIndex == 5) colors = [Color(0xFF00E5FF), Color(0xFFFF00FF)]; // Cyan-Pink
    else colors = [Color(0xFF6A5AF9), Color(0xFF00D1FF)]; // 7th Maya Original

    paint.shader = SweepGradient(colors: colors, transform: GradientRotation(t * 2 * math.pi))
        .createShader(Rect.fromLTWH(0,0,size.width,size.height));

    // بیچ والا M.A.Y.A کے گرد گولہ
    canvas.drawCircle(center, 150 + math.sin(t*2*math.pi)*10, paint);
    canvas.drawCircle(center, 170 + math.cos(t*2*math.pi)*10, paint..strokeWidth=6);
  }
  @override bool shouldRepaint(covariant _ThemePainter old) => old.t != t || old.themeIndex != themeIndex;
}

// دو ValueNotifier ایک ساتھ سننے کے لیے مددگار
class ValueListenableBuilder2<A,B> extends StatelessWidget {
  final ValueNotifier<A> first; final ValueNotifier<B> second;
  final Widget Function(BuildContext, A, B, Widget?) builder;
  const ValueListenableBuilder2({super.key, required this.first, required this.second, required this.builder});
  @override Widget build(BuildContext context){
    return ValueListenableBuilder<A>(valueListenable: first, builder: (c,a,_){
      return ValueListenableBuilder<B>(valueListenable: second, builder: (c,b,__){ return builder(c,a,b, null); });
    });
  }
}
