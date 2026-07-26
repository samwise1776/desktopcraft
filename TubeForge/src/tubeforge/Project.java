package tubeforge;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Project {
    public final List<MediaClip> clips = new ArrayList<>();
    public String title = "";
    public String channel = "";
    public String preset = "YouTube 1080p (16:9)";

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("title", title);
        p.setProperty("channel", channel);
        p.setProperty("preset", preset);
        p.setProperty("count", String.valueOf(clips.size()));
        for (int i = 0; i < clips.size(); i++) {
            MediaClip c = clips.get(i);
            p.setProperty("clip." + i + ".path", c.path().toString());
            p.setProperty("clip." + i + ".duration", String.valueOf(c.duration()));
            p.setProperty("clip." + i + ".in", String.valueOf(c.inPoint()));
            p.setProperty("clip." + i + ".out", String.valueOf(c.outPoint()));
        }
        try (Writer w = Files.newBufferedWriter(file)) { p.store(w, "TubeForge project"); }
    }

    public static Project load(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file)) { p.load(r); }
        Project project = new Project();
        project.title = p.getProperty("title", "");
        project.channel = p.getProperty("channel", "");
        project.preset = p.getProperty("preset", project.preset);
        int count = Integer.parseInt(p.getProperty("count", "0"));
        for (int i = 0; i < count; i++) {
            Path path = Path.of(p.getProperty("clip." + i + ".path"));
            double duration = Double.parseDouble(p.getProperty("clip." + i + ".duration"));
            double in = Double.parseDouble(p.getProperty("clip." + i + ".in"));
            double out = Double.parseDouble(p.getProperty("clip." + i + ".out"));
            project.clips.add(new MediaClip(path, duration, in, out));
        }
        return project;
    }
}
