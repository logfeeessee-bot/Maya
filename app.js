// === اپنی API Keys یہاں ڈالیں ===
const GROQ_API_KEY = "gsk_...اپنی کی یہاں ڈالیں";
const GEMINI_API_KEY = "AIza...اپنی کی یہاں ڈالیں";

// Particle Gola
const canvas = document.getElementById('particleCanvas');
const ctx = canvas.getContext('2d');
canvas.width=300; canvas.height=300;
let particles=[];
for(let i=0;i<400;i++){
  let theta=Math.random()*Math.PI*2; let phi=Math.acos(2*Math.random()-1);
  particles.push({theta, phi, r:120});
}
function draw(){
  ctx.clearRect(0,0,300,300);
  particles.forEach(p=>{
    p.theta+=0.005;
    let x=150 + p.r * Math.sin(p.phi) * Math.cos(p.theta);
    let y=150 + p.r * Math.sin(p.phi) * Math.sin(p.theta);
    let scale=(Math.cos(p.phi)+1.5)/2.5;
    let hue=(p.theta*180/Math.PI + p.phi*90) % 360;
    ctx.fillStyle=`hsl(${hue},100%,65%)`;
    ctx.beginPath(); ctx.arc(x,y,2.2*scale,0,Math.PI*2); ctx.fill();
  });
  requestAnimationFrame(draw);
}
draw();

// AI Logic - Groq Llama 3.3 se connect
async function handleAI(text){
  document.getElementById('statusText').innerText = "Soch rahi hai...";
  try {
    let res = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: { "Authorization": `Bearer ${GROQ_API_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "llama-3.3-70b-versatile",
        messages: [{role:"user", content: text}]
      })
    });
    let data = await res.json();
    let reply = data.choices[0].message.content;
    speak(reply);
  } catch(e){
    speak("Good evening, bolo kya chahiye?");
  }
}

function speak(text){
  document.getElementById('statusText').innerText='Bol rahi hai...';
  let utter=new SpeechSynthesisUtterance(text);
  utter.lang='hi-IN';
  utter.onend=()=>{ document.getElementById('statusText').innerText='Mayra chup hai'; };
  speechSynthesis.speak(utter);
}
function startListening(){
  const rec=new (window.SpeechRecognition||window.webkitSpeechRecognition)();
  rec.lang='hi-IN'; rec.start();
  document.getElementById('statusText').innerText='Sun rahi hai...';
  rec.onresult=(e)=>{ handleAI(e.results[0][0].transcript); }
}
function sendText(){ handleAI(document.getElementById('textInput').value); }
function openSettings(){ document.getElementById('settings').style.display='block'; }
function closeSettings(){ document.getElementById('settings').style.display='none'; }
function setTheme(t){
  if(t=='rainbow'){ document.documentElement.style.setProperty('--ring1','#ff00ff'); }
  if(t=='neon'){ document.documentElement.style.setProperty('--ring1','#00aaff'); }
  if(t=='green'){ document.documentElement.style.setProperty('--ring1','#00ff88'); }
  closeSettings();
    }
