package com.maya.aiassistant;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = new WebView(this);

        webView.setBackgroundColor(Color.rgb(5, 5, 12));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        String html =
                "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +

                "* { box-sizing: border-box; }" +

                "body {" +
                "margin:0;" +
                "background:#05050c;" +
                "color:white;" +
                "font-family:Arial,sans-serif;" +
                "height:100vh;" +
                "overflow:hidden;" +
                "}" +

                ".top {" +
                "height:80px;" +
                "display:flex;" +
                "align-items:center;" +
                "justify-content:space-between;" +
                "padding:0 24px;" +
                "}" +

                ".logo {" +
                "font-size:34px;" +
                "font-weight:bold;" +
                "background:linear-gradient(90deg,#ff8a3d,#ff3d9a,#8b5cff);" +
                "-webkit-background-clip:text;" +
                "color:transparent;" +
                "}" +

                ".settings {" +
                "width:52px;" +
                "height:52px;" +
                "border-radius:50%;" +
                "border:1px solid #777;" +
                "background:#080811;" +
                "color:white;" +
                "font-size:27px;" +
                "display:flex;" +
                "align-items:center;" +
                "justify-content:center;" +
                "}" +

                ".center {" +
                "height:calc(100vh - 80px);" +
                "display:flex;" +
                "flex-direction:column;" +
                "align-items:center;" +
                "justify-content:center;" +
                "margin-top:-30px;" +
                "}" +

                ".orb {" +
                "width:285px;" +
                "height:285px;" +
                "border-radius:50%;" +
                "background:radial-gradient(circle at 30% 25%,#ff55bd 0%,transparent 20%),radial-gradient(circle at 70% 30%,#8b5cff 0%,transparent 25%),radial-gradient(circle at 30% 70%,#00e5ff 0%,transparent 25%),radial-gradient(circle at 70% 70%,#75ff55 0%,transparent 25%),radial-gradient(circle,#262050,#080812 70%);" +
                "box-shadow:0 0 35px #b34cff,0 0 80px #4520a0;" +
                "animation:pulse 3s infinite ease-in-out;" +
                "}" +

                "@keyframes pulse {" +
                "0% {transform:scale(.95);}" +
                "50% {transform:scale(1.04);}" +
                "100% {transform:scale(.95);}" +
                "}" +

                ".status {" +
                "margin-top:65px;" +
                "font-size:20px;" +
                "color:#aaa;" +
                "}" +

                ".button {" +
                "margin-top:30px;" +
                "border:0;" +
                "border-radius:40px;" +
                "padding:18px 38px;" +
                "font-size:21px;" +
                "font-weight:bold;" +
                "color:white;" +
                "background:linear-gradient(90deg,#ff3d91,#9c4dff);" +
                "}" +

                "</style>" +
                "</head>" +

                "<body>" +

                "<div class='top'>" +
                "<div class='logo'>MAYA</div>" +
                "<div class='settings'>⚙</div>" +
                "</div>" +

                "<div class='center'>" +
                "<div class='orb'></div>" +
                "<div class='status'>MAYA chup hai</div>" +
                "<button class='button'>MAYA se bulwao</button>" +
                "</div>" +

                "</body>" +
                "</html>";

        webView.loadDataWithBaseURL(
                "https://maya.local/",
                html,
                "text/html",
                "UTF-8",
                null
        );

        setContentView(webView);
    }
  }
