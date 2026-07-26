package tubeforge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public final class RecordingService {
    public enum Source {
        SCREEN("Screen"), WEBCAM("Webcam");
        private final String label;
        Source(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private Process process;
    private Thread logReader;
    private final StringBuilder log = new StringBuilder();

    public synchronized void start(Source source, boolean microphone, int microphoneVolume, Path output)
            throws IOException {
        if (isRecording()) throw new IOException("A recording is already running.");
        List<String> command = command(source, microphone, microphoneVolume, output);
        process = new ProcessBuilder(command).redirectErrorStream(true).start();
        log.setLength(0);
        logReader = Thread.ofVirtual().start(() -> {
            try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[1024];
                for (int n; (n = reader.read(buffer)) >= 0;) {
                    synchronized (log) {
                        log.append(buffer, 0, n);
                        if (log.length() > 12000) log.delete(0, log.length() - 12000);
                    }
                }
            } catch (IOException ignored) {}
        });
        try {
            Thread.sleep(700);
            if (!process.isAlive()) {
                int code = process.exitValue();
                process = null;
                throw new IOException("Recorder could not start (FFmpeg code " + code + ").\n" + logTail());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Recording startup was interrupted.", e);
        }
    }

    public synchronized void stop() throws IOException, InterruptedException {
        if (!isRecording()) return;
        process.getOutputStream().write("q\n".getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().flush();
        if (!process.waitFor(12, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroy();
            process.waitFor();
        }
        int code = process.exitValue();
        process = null;
        if (logReader != null) logReader.join(1000);
        if (code != 0) throw new IOException("Recording failed.\n" + logTail());
    }

    public synchronized boolean isRecording() {
        return process != null && process.isAlive();
    }

    private String logTail() {
        synchronized (log) {
            return log.length() > 2200 ? log.substring(log.length() - 2200) : log.toString();
        }
    }

    private static List<String> command(Source source, boolean microphone, int microphoneVolume,
                                        Path output)
            throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> c = new ArrayList<>(List.of("ffmpeg", "-y"));
        if (os.contains("linux")) {
            String display = System.getenv().getOrDefault("DISPLAY", ":0.0");
            if (source == Source.SCREEN) {
                c.addAll(List.of("-f", "x11grab", "-framerate", "30", "-i", display));
            } else {
                c.addAll(List.of("-f", "v4l2", "-framerate", "30", "-i", "/dev/video0"));
            }
            if (microphone) c.addAll(List.of("-f", "pulse", "-i", "default"));
        } else if (os.contains("win")) {
            if (source == Source.SCREEN) {
                c.addAll(List.of("-f", "gdigrab", "-framerate", "30", "-i", "desktop"));
            } else {
                c.addAll(List.of("-f", "dshow", "-i", "video=default"));
            }
            if (microphone) c.addAll(List.of("-f", "dshow", "-i", "audio=default"));
        } else if (os.contains("mac")) {
            // AVFoundation device 1 is commonly the display; device 0 is commonly the camera.
            String devices = source == Source.SCREEN ? "1" : "0";
            c.addAll(List.of("-f", "avfoundation", "-framerate", "30", "-i",
                    devices + (microphone ? ":0" : ":none")));
        } else {
            throw new IOException("Recording is not supported on " + System.getProperty("os.name"));
        }
        c.addAll(List.of("-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
                "-pix_fmt", "yuv420p"));
        if (microphone) {
            double gain = Math.max(0, Math.min(200, microphoneVolume)) / 100.0;
            c.addAll(List.of("-af", "volume=" + gain, "-c:a", "aac", "-b:a", "192k"));
        }
        c.addAll(List.of("-movflags", "+faststart", output.toString()));
        return c;
    }
}
