import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:speech_to_text/speech_to_text.dart';
import 'orb.dart';

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
  final stt = SpeechToText();
  final tts = FlutterTts();
  final input = TextEditingController();
  final List<Map<String, dynamic>> history = [];
  String apiKey = '';
  String model = 'gemini-2.5-flash';
  String lang = 'hi_IN';
  String reply = '';
  bool listening = false;
  bool busy = false;

  @override
  void initState() {
    super.initState();
    _ctl = AnimationController(
        vsync: this, duration: const Duration(seconds: 6))
      ..repeat();
    _load();
  }

  Future<void> _load() async {
    final p = await SharedPreferences.getInstance();
    apiKey = p.getString('key') ?? '';
    model = p.getString('model') ?? model;
    lang = p.getString('lang') ?? lang;
    await tts.setLanguage(lang.replaceAll('_', '-'));
    await tts.setSpeechRate(0.5);
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _ctl.dispose();
    input.dispose();
    super.dispose();
  }

  void _openSettings() {
    final k = TextEditingController(text: apiKey);
    final m = TextEditingController(text: model);
    final l = TextEditingController(text: lang);
    showDialog(
      context: context,
      builder: (c) => AlertDialog(
        backgroundColor: kCard,
        title: const Text('Settings'),
        content: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            TextField(
                controller: k,
                obscureText: true,
                decoration: const InputDecoration(labelText: 'Gemini API key')),
            TextField(
                controller: m,
                decoration: const InputDecoration(labelText: 'Model')),
            TextField(
                controller: l,
                decoration:
                    const InputDecoration(labelText: 'Bhasha (hi_IN / en_US)')),
          ]),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(c), child: const Text('Cancel')),
          TextButton(
            onPressed: () async {
              final p = await SharedPreferences.getInstance();
              apiKey = k.text.trim();
              model = m.text.trim();
              lang = l.text.trim();
              await p.setString('key', apiKey);
              await p.setString('model', model);
              await p.setString('lang', lang);
              await tts.setLanguage(lang.replaceAll('_', '-'));
              if (c.mounted) Navigator.pop(c);
              if (mounted) setState(() {});
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  Future<void> _toggleMic() async {
    if (listening) {
      await stt.stop();
      setState(() => listening = false);
      return;
    }
    if (apiKey.isEmpty) {
      _openSettings();
      return;
    }
    await tts.stop();
    final ok = await stt.initialize(
      onError: (e) {
        if (mounted) setState(() => listening = false);
      },
      onStatus: (s) {
        if ((s == 'done' || s == 'notListening') && mounted) {
          setState(() => listening = false);
        }
      },
    );
    if (!ok) {
      setState(() => reply =
          'Mic nahi chal raha. Phone Settings > Apps > Maya > Permissions mein Microphone allow karo.');
      return;
    }
    setState(() => listening = true);
    await stt.listen(
      localeId: lang,
      listenFor: const Duration(seconds: 30),
      pauseFor: const Duration(seconds: 3),
      onResult: (r) {
        if (r.finalResult) {
          setState(() => listening = false);
          _ask(r.recognizedWords);
        }
      },
    );
  }

  Future<void> _ask(String text) async {
    text = text.trim();
    if (text.isEmpty || busy) return;
    if (apiKey.isEmpty) {
      _openSettings();
      return;
    }
    setState(() {
      busy = true;
      reply = 'Soch rahi hoon...';
    });
    history.add({
      'role': 'user',
      'parts': [
        {'text': text}
      ]
    });
    String out;
    bool isError = false;
    try {
      final res = await http
          .post(
            Uri.parse(
                'https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent'),
            headers: {
              'Content-Type': 'application/json',
              'x-goog-api-key': apiKey,
            },
            body: jsonEncode({
              'system_instruction': {
                'parts': [
                  {
                    'text':
                        'Tum Maya ho, ek dost jaisi female voice assistant. User ki bhasha mein (Hindi, Hinglish ya English) chhote aur saaf jawab do, 1 se 3 vakya. Markdown, star ya emoji mat likho.'
                  }
                ]
              },
              'contents': history,
            }),
          )
          .timeout(const Duration(seconds: 40));
      final data = jsonDecode(res.body);
      if (res.statusCode == 200) {
        out = (data['candidates'][0]['content']['parts'][0]['text'] ?? '')
            .toString()
            .replaceAll('*', '')
            .trim();
        history.add({
          'role': 'model',
          'parts': [
            {'text': out}
          ]
        });
        if (history.length > 20) {
          history.removeRange(0, history.length - 20);
        }
      } else {
        isError = true;
        out =
            'Error ${res.statusCode}: ${data['error']?['message'] ?? res.body}';
        history.removeLast();
      }
    } catch (e) {
      isError = true;
      out =
          'Internet ya server ki dikkat hai. Offline mode agle din jodenge.';
      history.removeLast();
    }
    if (!mounted) return;
    setState(() {
      busy = false;
      reply = out;
    });
    if (!isError) await tts.speak(out);
  }

  Widget chip(IconData i, String t) => Expanded(
        child: Container(
          height: 56,
          margin: const EdgeInsets.symmetric(horizontal: 5),
          decoration: BoxDecoration(
              color: const Color(0x66151D2E),
              borderRadius: BorderRadius.circular(18)),
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
              color: const Color(0x66151D2E),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: Colors.white24)),
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

  Widget orbLine() =>
      Container(width: 36, height: 1, color: const Color(0x66FFFFFF));

  @override
  Widget build(BuildContext context) {
    final active = listening || busy;
    final sub = listening
        ? 'LISTENING...'
        : busy
            ? 'THINKING...'
            : 'HOW CAN I HELP YOU?';
    return Scaffold(
      floatingActionButtonLocation: FloatingActionButtonLocation.centerDocked,
      floatingActionButton: SizedBox(
        width: 68,
        height: 68,
        child: FloatingActionButton(
          backgroundColor: kBlue,
          shape: const CircleBorder(),
          onPressed: _toggleMic,
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
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFF0B1220), Color(0xFF13203F), Color(0xFF0E1830)],
          ),
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Column(children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const SizedBox(width: 48),
                  const Text('Maya',
                      style: TextStyle(
                          fontSize: 26, fontWeight: FontWeight.bold)),
                  IconButton(
                      onPressed: _openSettings,
                      icon: const Icon(Icons.settings, size: 26)),
                ],
              ),
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
              const SizedBox(height: 10),
              const Align(
                alignment: Alignment.centerLeft,
                child: Text('Good morning,',
                    style: TextStyle(fontSize: 22, color: Colors.white54)),
              ),
              const Align(
                alignment: Alignment.centerLeft,
                child: Text('there',
                    style:
                        TextStyle(fontSize: 30, fontWeight: FontWeight.bold)),
              ),
              Align(
                alignment: Alignment.centerLeft,
                child: Text(
                    reply.isEmpty ? 'Maya is ready to help you.' : '',
                    style: const TextStyle(
                        fontSize: 15, color: Colors.white54)),
              ),
              if (reply.isNotEmpty)
                Container(
                  width: double.infinity,
                  margin: const EdgeInsets.only(top: 4),
                  padding: const EdgeInsets.all(12),
                  constraints: const BoxConstraints(maxHeight: 100),
                  decoration: BoxDecoration(
                      color: kCard, borderRadius: BorderRadius.circular(16)),
                  child: SingleChildScrollView(
                      child:
                          Text(reply, style: const TextStyle(fontSize: 15))),
                ),
              Expanded(
                child: Center(
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    child: AnimatedBuilder(
                      animation: _ctl,
                      builder: (context, _) => SizedBox(
                        width: 360,
                        height: 360,
                        child: CustomPaint(
                          painter: OrbPainter(_ctl.value, active),
                          child: Center(
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    orbLine(),
                                    const SizedBox(width: 8),
                                    const Text('AI ASSISTANT',
                                        style: TextStyle(
                                            fontSize: 10,
                                            letterSpacing: 2,
                                            color: Colors.white60)),
                                    const SizedBox(width: 8),
                                    orbLine(),
                                  ],
                                ),
                                ShaderMask(
                                  shaderCallback: (r) => const LinearGradient(
                                          colors: [
                                        Color(0xFFE6E8F0),
                                        Color(0xFF6C8CFF),
                                        Color(0xFFE066C8)
                                      ]).createShader(r),
                                  child: const Text('M.A.Y.A',
                                      style: TextStyle(
                                          fontSize: 40,
                                          fontWeight: FontWeight.bold,
                                          color: Colors.white)),
                                ),
                                const SizedBox(height: 22),
                                Text(sub,
                                    style: const TextStyle(
                                        fontSize: 10,
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
                padding: const EdgeInsets.only(left: 16, right: 6),
                decoration: BoxDecoration(
                    color: const Color(0x66151D2E),
                    borderRadius: BorderRadius.circular(30),
                    border: Border.all(color: Colors.white24)),
                child: Row(children: [
                  const Icon(Icons.attach_file, color: Colors.white54),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: input,
                      style: const TextStyle(fontSize: 17),
                      decoration: const InputDecoration(
                          hintText: 'Ask Maya anything...',
                          border: InputBorder.none),
                      onSubmitted: (v) {
                        input.clear();
                        _ask(v);
                      },
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.send, color: Colors.white54),
                    onPressed: () {
                      final v = input.text;
                      input.clear();
                      _ask(v);
                    },
                  ),
                ]),
              ),
              const SizedBox(height: 10),
            ]),
          ),
        ),
      ),
    );
  }
}
