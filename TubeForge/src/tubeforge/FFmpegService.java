package tubeforge;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

public final class FFmpegService {
    public double probeDuration(Path file) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries",
                "format=duration", "-of", "default=noprint_wrappers=1:nokey=1",
                file.toString()).redirectErrorStream(true).start();
        String value = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (p.waitFor() != 0) throw new IOException("Could not read " + file.getFileName());
        return Double.parseDouble(value);
    }

    public boolean hasAudio(Path file) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("ffprobe", "-v", "error", "-select_streams", "a:0",
                "-show_entries", "stream=index", "-of", "csv=p=0", file.toString())
                .redirectErrorStream(true).start();
        String value = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (p.waitFor() != 0) throw new IOException("Could not inspect " + file.getFileName());
        return !value.isBlank();
    }

    public BufferedImage thumbnail(MediaClip clip) throws IOException, InterruptedException {
        Path image = Files.createTempFile("tubeforge-preview-", ".jpg");
        double at = clip.inPoint() + Math.min(clip.editedDuration() / 2, 5);
        Process p = new ProcessBuilder("ffmpeg", "-y", "-ss", String.valueOf(at),
                "-i", clip.path().toString(), "-frames:v", "1", "-vf",
                "scale=960:-2", image.toString()).redirectErrorStream(true).start();
        p.getInputStream().transferTo(OutputStream.nullOutputStream());
        int code = p.waitFor();
        BufferedImage result = code == 0 ? ImageIO.read(image.toFile()) : null;
        Files.deleteIfExists(image);
        return result;
    }

    public void export(Project project, Path output, Consumer<String> status)
            throws IOException, InterruptedException {
        if (project.clips.isEmpty()) throw new IOException("Import at least one clip.");
        int[] size = dimensions(project.preset);
        Path temp = Files.createTempDirectory("tubeforge-export-");
        try {
            List<Path> segments = new ArrayList<>();
            for (int i = 0; i < project.clips.size(); i++) {
                MediaClip c = project.clips.get(i);
                status.accept("Rendering clip " + (i + 1) + " of " + project.clips.size());
                Path segment = temp.resolve("clip-%04d.mp4".formatted(i));
                String vf = videoFilter(size[0], size[1], i == 0 ? project.title : "",
                        i == 0 ? project.channel : "");
                boolean audio = hasAudio(c.path());
                List<String> cmd = new ArrayList<>(List.of("ffmpeg", "-y", "-ss",
                        String.valueOf(c.inPoint()), "-t", String.valueOf(c.editedDuration()),
                        "-i", c.path().toString()));
                if (!audio) {
                    cmd.addAll(List.of("-f", "lavfi", "-t", String.valueOf(c.editedDuration()),
                            "-i", "anullsrc=r=48000:cl=stereo"));
                }
                cmd.addAll(List.of("-map", "0:v:0", "-map", audio ? "0:a:0" : "1:a:0",
                        "-vf", vf, "-c:v", "libx264",
                        "-preset", "medium", "-crf", "18", "-c:a", "aac", "-b:a", "192k",
                        "-ar", "48000", "-ac", "2", "-shortest", "-movflags", "+faststart",
                        segment.toString()));
                run(cmd);
                segments.add(segment);
            }
            status.accept("Joining clips and optimizing for YouTube");
            Path list = temp.resolve("clips.txt");
            StringBuilder content = new StringBuilder();
            for (Path segment : segments)
                content.append("file '").append(segment.toString().replace("'", "'\\''")).append("'\n");
            Files.writeString(list, content);
            run(List.of("ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", list.toString(),
                    "-c", "copy", "-movflags", "+faststart", output.toString()));
        } finally {
            try (var files = Files.walk(temp)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    private static int[] dimensions(String preset) {
        if (preset.contains("Shorts")) return new int[]{1080, 1920};
        if (preset.contains("4K")) return new int[]{3840, 2160};
        if (preset.contains("1440")) return new int[]{2560, 1440};
        return new int[]{1920, 1080};
    }

    private static String videoFilter(int w, int h, String title, String channel) {
        String base = "scale=%d:%d:force_original_aspect_ratio=decrease,".formatted(w, h)
                + "pad=%d:%d:(ow-iw)/2:(oh-ih)/2:black,setsar=1,fps=30".formatted(w, h);
        if (title.isBlank() && channel.isBlank()) return base;
        String text = title + (channel.isBlank() ? "" : "  •  " + channel);
        String escaped = text.replace("\\", "\\\\").replace(":", "\\:")
                .replace("'", "\\'").replace("%", "\\%");
        return base + ",drawbox=x=0:y=ih-170:w=iw:h=170:color=black@0.62:t=fill:"
                + "enable='between(t,0,5)',"
                + "drawtext=text='" + escaped + "':fontcolor=white:fontsize=48:"
                + "x=70:y=h-110:enable='between(t,0,5)'";
    }

    private static void run(List<String> command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) {
            String tail = log.length() > 1800 ? log.substring(log.length() - 1800) : log;
            throw new IOException("FFmpeg export failed:\n" + tail);
        }
    }
}
