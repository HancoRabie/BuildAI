package com.hanco.buildai.client;

import java.awt.Desktop;
import java.net.URI;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Launch only a verified HTTPS authorization URL using the native system browser.
 * Desktop.browse() is frequently unsupported by Minecraft's bundled Java runtime
 * on Windows, despite the machine having a default browser installed.
 *
 * No command shell is involved: the OAuth URL is passed as ONE process argument,
 * so '&' characters in the query string are never interpreted as shell syntax.
 */
public final class BrowserLauncher {
    private BrowserLauncher(){}

    public static boolean openOpenAiAuthorization(String rawUrl) {
        URI uri=URI.create(rawUrl);
        if(!"https".equalsIgnoreCase(uri.getScheme())
            || !"auth.openai.com".equalsIgnoreCase(uri.getHost()))
            throw new IllegalArgumentException("Only official OpenAI authorization pages may be opened");
        if(Desktop.isDesktopSupported()) {
            try {
                Desktop desktop=Desktop.getDesktop();
                if(desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(uri);
                    return true;
                }
            } catch(Exception | LinkageError ignored) {
                // Native OS strategy below; do not record or log the authorization URL.
            }
        }
        for(List<String> command:nativeCommands(uri.toString())) {
            try{
                new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
                return true;
            }catch(IOException | SecurityException ignored) {
                // Try next fallback or let the user copy the live sign-in URL.
            }
        }
        return false;
    }

    static List<List<String>> nativeCommands(String url) {
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        if(os.contains("win"))
            return List.of(
                List.of("rundll32.exe","url.dll,FileProtocolHandler",url),
                List.of("explorer.exe",url)
            );
        if(os.contains("mac"))return List.of(List.of("open",url));
        return List.of(List.of("xdg-open",url),List.of("gio","open",url));
    }
}
