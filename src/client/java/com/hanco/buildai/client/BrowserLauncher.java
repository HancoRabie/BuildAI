package com.hanco.buildai.client;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Opens OpenAI authorization with the CURRENT OS USER'S default HTTPS handler.
 * No browser names, executable paths, or shell command interpolation are used.
 *
 * Minecraft's embedded Java runtime sometimes disables Desktop.BROWSE on Windows.
 * Windows' registered URL protocol handler works with whichever browser that user
 * selected in Settings > Apps > Default apps (Brave, Firefox, Edge, etc.).
 */
public final class BrowserLauncher {
    private BrowserLauncher() {}

    public static boolean openOpenAiAuthorization(String rawUrl) {
        URI uri=URI.create(rawUrl);
        if(!"https".equalsIgnoreCase(uri.getScheme())
            || !"auth.openai.com".equalsIgnoreCase(uri.getHost()))
            throw new IllegalArgumentException("Only official OpenAI authorization pages may be opened");

        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);

        // Prefer the Windows HTTPS association *before* relying on Java's AWT
        // Desktop implementation. Do not bypass Brave or another chosen default.
        if(os.contains("win")){
            if(launch(List.of("rundll32.exe","url.dll,FileProtocolHandler",uri.toString())))
                return true;
            if(browseWithJava(uri))return true;
            return launch(List.of("explorer.exe",uri.toString()));
        }

        // On macOS and Linux, use the platform's default browser association.
        if(browseWithJava(uri))return true;
        for(List<String> command:nativeCommands(uri.toString(),os)){
            if(launch(command))return true;
        }
        return false;
    }

    /** Explicit PUBLIC GitHub submission, never an automatic file upload.
     * The user must sign into GitHub and attach the reviewed ZIP manually. */
    public static boolean openBuildAICommunityFeedback(String category){
        URI uri=URI.create(
            com.hanco.buildai.blueprint.CommunityFeedbackPackage.submissionUrl(category));
        if(!"https".equalsIgnoreCase(uri.getScheme())||
           !"github.com".equalsIgnoreCase(uri.getHost())||
           !"/HancoRabie/BuildAI/issues/new".equals(uri.getPath()))
            throw new IllegalStateException("Invalid official BuildAI GitHub submission URL");
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        if(os.contains("win"))
            return launch(List.of("rundll32.exe","url.dll,FileProtocolHandler",uri.toString()))||
                browseWithJava(uri)||launch(List.of("explorer.exe",uri.toString()));
        if(browseWithJava(uri))return true;
        for(List<String> cmd:nativeCommands(uri.toString(),os))
            if(launch(cmd))return true;
        return false;
    }

    private static boolean browseWithJava(URI uri) {
        try {
            if(Desktop.isDesktopSupported()){
                Desktop desktop=Desktop.getDesktop();
                if(desktop.isSupported(Desktop.Action.BROWSE)){
                    desktop.browse(uri);
                    return true;
                }
            }
        }catch(Exception | LinkageError ignored){
            // Continue to the operating system's native default-browser handler.
        }
        return false;
    }

    private static boolean launch(List<String> args) {
        try {
            // No shell: URL is one argument, so its OAuth query params aren't run as commands.
            new ProcessBuilder(args)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
            return true;
        }catch(IOException | SecurityException ignored){
            return false;
        }
    }

    static List<List<String>> nativeCommands(String url,String os) {
        if(os.contains("mac"))return List.of(List.of("open",url));
        return List.of(List.of("xdg-open",url),List.of("gio","open",url));
    }
}
