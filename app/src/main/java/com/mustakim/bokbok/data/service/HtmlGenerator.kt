package com.mustakim.bokbok.data.service

import android.content.Context
import com.mustakim.bokbok.R
import com.mustakim.bokbok.utils.formatDuration
import com.mustakim.bokbok.utils.formatSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HtmlGenerator(private val context: Context) {

    private val css = CssGenerator(context)
    private val js = JsGenerator()

    private fun getHead(titleSuffix: String = ""): String {
        val title = context.getString(R.string.app_name) + titleSuffix
        return """
            <head>
                <meta charset='UTF-8'>
                <title>$title</title>
                <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0'>
                <style>${css.getCss()}</style>
            </head>
        """.trimIndent()
    }

    fun getLoginPage(): String {
        val inputs = (1..SecurityManager.PASSWORD_LENGTH).joinToString("") { 
            """<input class="pass" type="number" maxlength="1" oninput="this.value=this.value.slice(0,this.maxLength)"/>"""
        }
        
        return """
            <html>
                ${getHead(" - Login")}
                <body>
                    <div id="cp">
                        <label>Enter PIN</label>
                        <div id="cpi">$inputs</div>
                    </div>
                    <form method="post" id="l">
                        <input type="hidden" name="p" id="p"/>
                    </form>
                    <script>${js.getLoginJs()}</script>
                </body>
            </html>
        """.trimIndent()
    }

    fun getIndexPage(files: List<File>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        
        val rows = files.joinToString("") { file ->
            val name = file.name
            val date = dateFormat.format(Date(file.lastModified()))
            val size = formatSize(file.length())
            // Duration logic would need MediaMetadataRetriever, simplifying for now
            val duration = "" 

            """
            <tr>
                <td>$name</td>
                <td>$date</td>
                <td>$size</td>
                <td class="actions">
                    <div class="play" onclick="play('${file.name}')"></div>
                    <a class="download" href="/${file.name}" download></a>
                </td>
            </tr>
            """.trimIndent()
        }

        return """
            <html>
                ${getHead()}
                <body>
                    <div id="title">
                        <div>
                            <span>${context.getString(R.string.app_name)}</span>
                            <span>${files.size} Files</span>
                        </div>
                    </div>
                    
                    <table>
                        <tr>
                            <th>Name</th>
                            <th>Date</th>
                            <th>Size</th>
                            <th>Actions</th>
                        </tr>
                        $rows
                    </table>
                    
                    <div id="player-overlay" onclick="closePlayer()">
                        <video id="video-player" controls onclick="event.stopPropagation()"></video>
                    </div>

                    <script>${js.getListJs()}</script>
                </body>
            </html>
        """.trimIndent()
    }

    fun getErrorPage(code: Int, message: String): String {
        return """
            <html>
                ${getHead()}
                <body>
                    <div id="containerError">
                        <span id="number">$code</span>
                        <span id="message">$message</span>
                    </div>
                </body>
            </html>
        """.trimIndent()
    }
}

class CssGenerator(private val context: Context) {
    fun getCss(): String {
        // Condensed generic dark theme CSS
        return """
            :root {
                --bg: #121212;
                --surface: #1E1E1E;
                --primary: #BB86FC;
                --text: #E1E1E1;
            }
            body { background-color: var(--bg); color: var(--text); font-family: sans-serif; margin: 0; padding: 20px; }
            table { width: 100%; border-collapse: collapse; margin-top: 20px; }
            th, td { text-align: left; padding: 12px; border-bottom: 1px solid #333; }
            tr:hover td { background-color: var(--surface); }
            .actions { display: flex; gap: 10px; }
            .play, .download { width: 24px; height: 24px; cursor: pointer; filter: invert(1); opacity: 0.7; }
            .play:hover, .download:hover { opacity: 1; }
            .play { background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M8 5v14l11-7z'/%3E%3C/svg%3E") no-repeat center; }
            .download { background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z'/%3E%3C/svg%3E") no-repeat center; }
            
            /* Login CSS */
            #cp { position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); text-align: center; }
            #cpi { display: flex; gap: 10px; margin-top: 20px; }
            .pass { width: 50px; height: 60px; font-size: 2rem; text-align: center; background: var(--surface); border: none; color: var(--text); border-radius: 8px; }
            .pass:focus { outline: 2px solid var(--primary); }
            
            #player-overlay { display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.9); justify-content: center; align-items: center; }
            #video-player { max-width: 90%; max-height: 90%; }
        """.trimIndent()
    }
}

class JsGenerator {
    fun getLoginJs(): String {
        return """
            const inputs = document.querySelectorAll('.pass');
            inputs.forEach((input, index) => {
                input.addEventListener('input', (e) => {
                    if(e.target.value.length === 1) {
                        if(index < inputs.length - 1) inputs[index + 1].focus();
                        checkSubmit();
                    }
                });
                input.addEventListener('keydown', (e) => {
                    if(e.key === 'Backspace' && e.target.value === '' && index > 0) {
                        inputs[index - 1].focus();
                    }
                });
            });

            function checkSubmit() {
                let code = '';
                inputs.forEach(i => code += i.value);
                if(code.length === ${SecurityManager.PASSWORD_LENGTH}) {
                    document.getElementById('p').value = code;
                    document.getElementById('l').submit();
                }
            }
        """.trimIndent()
    }

    fun getListJs(): String {
        return """
            function play(filename) {
                const video = document.getElementById('video-player');
                video.src = '/' + filename;
                document.getElementById('player-overlay').style.display = 'flex';
                video.play();
            }
            function closePlayer() {
                const video = document.getElementById('video-player');
                video.pause();
                video.src = '';
                document.getElementById('player-overlay').style.display = 'none';
            }
        """.trimIndent()
    }
}
